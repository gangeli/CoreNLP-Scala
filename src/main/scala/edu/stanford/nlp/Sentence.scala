package edu.stanford.nlp;

import scala.collection.JavaConversions._
import scala.collection.MapLike
import scala.collection.Map
import scala.collection.generic.CanBuildFrom
import scala.concurrent.Lock

import java.io.ObjectInputStream
import java.lang.ref.SoftReference
import java.lang.ref.ReferenceQueue
import java.util.Properties

import edu.stanford.nlp.classify.LinearClassifierFactory
import edu.stanford.nlp.classify.LogPrior
import edu.stanford.nlp.classify.RVFDataset
import edu.stanford.nlp.ie.NERClassifierCombiner
import edu.stanford.nlp.io.IOUtils
import edu.stanford.nlp.ling.HasWord
import edu.stanford.nlp.ling.RVFDatum
import edu.stanford.nlp.ling.Word
import edu.stanford.nlp.ling.CoreLabel
import edu.stanford.nlp.optimization.DiffFunction
import edu.stanford.nlp.optimization.QNMinimizer
import edu.stanford.nlp.optimization.SGDToQNMinimizer
import edu.stanford.nlp.parser.lexparser.LexicalizedParser
import edu.stanford.nlp.process.Morphology
import edu.stanford.nlp.process.PTBTokenizer
import edu.stanford.nlp.stats.ClassicCounter
import edu.stanford.nlp.stats.Counter
import edu.stanford.nlp.tagger.maxent.MaxentTagger
import edu.stanford.nlp.trees.CollinsHeadFinder
import edu.stanford.nlp.trees.LabeledScoredTreeNode
import edu.stanford.nlp.trees.Tree
import edu.stanford.nlp.trees.Trees
import edu.stanford.nlp.trees.GrammaticalStructureFactory
import edu.stanford.nlp.trees.GrammaticalStructure
import edu.stanford.nlp.trees.PennTreebankLanguagePack
import edu.stanford.nlp.trees.TypedDependency
import edu.stanford.nlp.util.logging.Redwood.Util._

import NLPConfig._
import NLP._

object Sentence {
  val tokenizerFactory = PTBTokenizer.factory
  val grammaticalStructureFactory
    = new PennTreebankLanguagePack().grammaticalStructureFactory
  
  def apply(word:Seq[String]):Sentence = new Sentence(word.toArray)
  def apply(gloss:String):Sentence = new Sentence(gloss)
}


@SerialVersionUID(2l)
case class Sentence(word:Array[String]) extends CoreLabelSeq {

  def this(word:Seq[String]) = this(word.toArray)

  def this(sentence:String) = this(
    Sentence.tokenizerFactory.getTokenizer(new java.io.StringReader(sentence))
      .tokenize
      .map( _.word )
      .toArray
  )

  //
  // Necessary Overrides for Seq[CoreLabel]
  //
  override def length:Int = word.length
  override def apply(index:Int):CoreLabel = {
    val label = new CoreLabel(8)
    label.setWord(word(index))
    label.setTag(pos(index))
    if (index > 0) { label.setAfter(word(index - 1)) }
    if (index < word.length - 1) { label.setBefore(word(index + 1)) }
    label.setNER(ner(index))
    label.setLemma(lemma(index))
    label.setIndex(index)
    // TODO(gabor) things like character offsets, original text, etc.
    label
  }



  var id:Option[Int] = None
  // values
  lazy val parse:Tree = {
    NLP.parser.parse(word.toList, pos.toList)
  }

  lazy val stanfordDependencies:Array[(Int, String)] = {
    if (length == 0) {
      new Array[(Int, String)](0)
    } else {
      val depArray = new Array[(Int, String)](length)
      // (get dependencies)
      val structure:GrammaticalStructure
        = Sentence.grammaticalStructureFactory.newGrammaticalStructure(parse)
      val deps:java.util.Collection[TypedDependency]
        = structure.typedDependencies()
      // (fill dependencies)
      deps.foreach{ (arc:TypedDependency) =>
        depArray(arc.dep.index - 1) = 
          ( arc.gov.index - 1,
            arc.reln.getShortName + {if (arc.reln.getSpecific == null) "" else "_" + arc.reln.getSpecific} )
      }
      // (pad empty dependencies)
      for (i <- 0 until depArray.length) {
        if (depArray(i) == null) depArray(i) = (i, "noop")
      }
      depArray
    }
  }

  def dependencyRoot:Int
    = stanfordDependencies.zipWithIndex.filter( _._1._1 < 0 ).headOption match {
      case Some( (dep, index) ) => index
      case None => throw new IllegalStateException("Could not find head: '" +
                    this + "' --- dependencies: " + stanfordDependencies.mkString(" "))
    }

  def dependencyChild(root:Int, depType:String):Option[Int]
    = stanfordDependencies.zipWithIndex.filter( x => x._1._1 == root && x._1._2 == depType )
                                       .map( _._2 ).headOption

  def dependencyChildren(root:Int):Seq[(Int, String)]
    = stanfordDependencies.zipWithIndex.filter( _._1._1 == root ).map( x => (x._2, x._1._2) )
  
  def dependencyYield(root:Int):Set[Int] = {
    def recursiveSearch(root:Int, seen:Set[Int]):Set[Int] = {
      val directChildren = dependencyChildren(root).map( _._1 )
      directChildren.foldLeft(seen) {
          case (soFar:Set[Int], index:Int) =>
        if (!soFar(index)) recursiveSearch(index, seen + index)
        else soFar
      }
    }
    recursiveSearch(root, Set[Int](root))
  }

  def dependencyPathMonotonic(ancestor:Int, descendent:Int):Option[Seq[Int]] = {
    def recurse(ancestor:Int, descendent:Int, lst:List[Int]):Option[List[Int]] = {
      if (descendent == ancestor) Some(ancestor :: lst)
      else if (descendent < 0) None
      else recurse(ancestor, stanfordDependencies(descendent)._1, descendent :: lst)
    }
    recurse(ancestor, stanfordDependencies(descendent)._1, Nil)
  }

  lazy val headIndex:Int = {
    if (word.length == 1) { 0 }
    else {
      val headLeaf = parse.headTerminal(collinsHeadFinder)
      val index = parse.getLeaves().indexWhere{ (x:Tree) => x eq headLeaf }
      if (index < 0) word.length - 1 else index
    }
  }

  def headIndex(spanBegin:Int, spanEnd:Int):Int = {
    parse.setSpans
    val (score, tree) = parse.foldLeft( spanBegin + (length - spanEnd), parse ){
        case ( (smallestDiffSoFar:Int, bestTreeSoFar:Tree), tree:Tree ) =>
      if (tree != null && tree.getSpan != null) {
        val (treeBegin, treeEnd) = (tree.getSpan.getSource, tree.getSpan.getTarget)
        val diff = scala.math.abs(spanBegin - treeBegin)
                     + scala.math.abs(spanEnd - treeEnd)
        if (treeBegin >= spanBegin && treeEnd <= spanEnd &&
            diff < smallestDiffSoFar) { (diff, tree) }
        else { (smallestDiffSoFar, bestTreeSoFar) }
      } else { (smallestDiffSoFar, bestTreeSoFar) }
    }
    val headLeaf = tree.headTerminal(collinsHeadFinder)
    val index = parse.getLeaves().indexWhere{ (x:Tree) => x eq headLeaf }
    if (index < spanBegin || index >= spanEnd) spanEnd - 1 else index
  }
  
  def headWord(spanBegin:Int, spanEnd:Int):String = word(headIndex(spanBegin, spanEnd))

  lazy val pos:Array[String]
    = if (length == 0) new Array[String](0)
      else NLP.tagger.apply(word.toList).map( _.tag ).toArray

  lazy val lemma:Array[String] = word.zip(pos).map{ case (w:String,p:String) => 
        morph( m => m.lemma(w,p) ).toString
      }.toArray

  lazy val ner:Array[String] = nerCRF(word, pos)

  lazy val truecase:Array[String] = trueCaser(word, pos, lemma)

  // helper functions
  def words:Array[String] = word
  def tags:Array[String] = pos

  def headWord:String = word(headIndex)
  def headLemma:String = lemma(headIndex)
  def headPOS:String = pos(headIndex)
  def namedEntities:Array[(Array[String],String)] = {
    // (collect tags)
    val nerTags = word.zip(ner).foldLeft(List[(List[String],String)]()){
        case (soFar:List[(List[String],String)], (word:String, tag:String)) =>
      val (chunk, lastTag) = if (soFar.isEmpty) (List[String](), "O")
                             else soFar.head
      val tailList:List[(List[String],String)]
        = if (soFar.isEmpty) Nil else soFar.tail
      if (lastTag != tag) {
        (List[String](word), tag) :: {
          if (lastTag != "O") (chunk.reverse, lastTag) :: tailList
          else tailList
        }
      } else {
        (word :: chunk, tag) :: tailList
      }
    }
    // (some cleanup)
    val headPair = nerTags.head
    (if (headPair._2 == "O") nerTags.tail
     else (headPair._1.reverse, headPair._2) :: nerTags.tail)
      .reverse
      .map{ case (c,t) => (c.toArray,t) }
      .toArray
  }

  def toSentence:Sentence = this

  override def equals(a:Any):Boolean = {
    def seqMatch(s:Seq[String]):Boolean = {
      s.length == word.length && s.zip(word).forall{ case (a,b) => a == b }
    }
    a match {
      case (s:Sentence) =>
        for (id1 <- this.id;
             id2 <- s.id) return id1 == id2
        return seqMatch(s.word)
      case (s:Seq[String]) => seqMatch(s)
      case _ => false
    }
  }
  private var code:Int = 0
  override def hashCode:Int = {
    if (code == 0) { word.foreach( w => code = 37 * code + w.hashCode ) }
    code
  }
  override def toString:String = word.mkString(" ")
}
