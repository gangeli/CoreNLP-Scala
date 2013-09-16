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

object NLP {
  implicit def list2hasWordList(lst:Seq[String]):java.util.List[_<:HasWord]
    = lst.map( new Word(_) ).toList

  // ----------
  // Parsers
  // ----------
  lazy val stanfordParser = {
    val parser = LexicalizedParser.loadModel(parse.model)
    new {
      def parse(words:List[String], pos:List[String]):Tree = {
        parser.parseStrings(words);
      }
    }
  }
  lazy val parser = {
    try {                                  stanfordParser
    } catch { case (e:RuntimeException) => BerkeleyUtil.berkeleyParser
    }
  }
  // ----------
  // Stanford CoreNLP Components
  // ----------
  lazy val tagger = new MaxentTagger(pos.model)

  lazy val collinsHeadFinder = new CollinsHeadFinder()

  lazy val morph:((Morphology=>Any)=>Any) = {
    val morph = new Morphology()
    val morphLock = new Lock()
    val f = { (fn:Morphology=>Any) =>
      morphLock.acquire;
      val rtn = fn(morph);
      morphLock.release
      rtn
    }
    f
  }

  lazy val nerCRF:(Array[String], Array[String])=>Array[String] = {
    val classifier = new NERClassifierCombiner(ner.model, ner.aux);
    (words:Array[String], pos:Array[String]) => {
      val offsets:List[Int] = words.foldLeft( (List[Int](), 0) ){
          case ((offsetsSoFar:List[Int], offset:Int), word:String) =>
        (offset :: offsetsSoFar, offset + word.length + 1)
      }._1.reverse
      // (construct CoreLabel sentence)
      val coreSentence = new java.util.ArrayList[CoreLabel](words.length)
      words.zip(pos).zip(offsets)foreach{
          case ((word:String, pos:String), offset:Int) =>
        val label = new CoreLabel
        label.setWord(word)
        label.setOriginalText(word)
        label.setTag(pos)
        label.setBeginPosition(offset)
        label.setEndPosition(offset + word.length)
        coreSentence.add(label)
      }
      // (classify)
      classifier.classifySentence(coreSentence)
      val output:java.util.List[CoreLabel] = classifier.classifySentence(coreSentence);
      // (convert back)
      output.map{ (label:CoreLabel) =>
        label.ner()
      }.toArray
    }
  }


  // ----------
  // Methods
  // ----------
  def preload(obj: => Any) { new Thread(){ override def run:Unit = obj }.start }

}
