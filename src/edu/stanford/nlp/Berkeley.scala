package edu.stanford.nlp;

import scala.collection.JavaConversions._
import scala.concurrent.Lock

import edu.stanford.nlp.trees.Tree
import edu.stanford.nlp.trees.Trees
import edu.stanford.nlp.trees.LabeledScoredTreeNode
import edu.stanford.nlp.ling.HasWord
import edu.stanford.nlp.ling.Word

import edu.berkeley.nlp.PCFGLA._
import edu.berkeley.nlp.util.Numberer

import NLPConfig._

object BerkeleyUtil {
  type BerkeleyTree = edu.berkeley.nlp.syntax.Tree[String]

  implicit def stanfordTree2BerkeleyTree(btree:BerkeleyTree):Tree = {
    val roots = TreeAnnotations.unAnnotateTree(btree).getChildren;
    if (roots.isEmpty) {
      new LabeledScoredTreeNode();
    } else {
      def convert(src:BerkeleyTree):Tree = {
        val dst:Tree = new LabeledScoredTreeNode
        if (src.getLabel != null) dst.setLabel(new Word(src.getLabel))
        dst.setChildren(src.getChildren.map( convert(_) ).toArray)
        dst
      }
      new LabeledScoredTreeNode(new Word("TOP"),
                                List[Tree](convert(roots.get(0))))
    }
  }
  
  lazy val berkeleyParser = {
    // (function to create parser)
    def mkParser = {
      // (setup parser)
      val pData = ParserData.Load(parse.model)
      if (pData == null) throw new RuntimeException("Failed to load Berkeley parser model")
      val grammar = pData.getGrammar();
      val lexicon = pData.getLexicon();
      Numberer.setNumberers(pData.getNumbs());
      // (create parser object)
      val parser = new CoarseToFineMaxRuleParser(
                   grammar, lexicon, 1.0, -1, false, false, false,
                   false, false, true, true)
      // (set binarization)
      try {
        val binarizationField = classOf[ConstrainedArrayParser].getDeclaredField("binarization");
        binarizationField.setAccessible(true);
        binarizationField.set(parser, pData.getBinarization());
        binarizationField.setAccessible(false);
      } catch { case (e:Exception) => throw new RuntimeException(e) }
      // (parser object)
      new {
        def parse(words:List[String], pos:List[String]):Tree = {
          var parsedTree:BerkeleyTree 
            = parser.getBestConstrainedParse(words, pos, null);
          if (parsedTree.getChildren().isEmpty()) {
            parsedTree = parser.getBestConstrainedParse(words, null, null);
          }
          parsedTree
        }
      }
    }
    // (create parsers)
    val parsers = (0 until numThreads).map{ x => (mkParser, new Lock) }.toList
    // (multithreaded implementation)
    new {
      def parse(words:List[String], pos:List[String]):Tree = {
        def tryParse:Tree = {
          val validParser = parsers.indexWhere{
            (pair:({def parse(words:List[String],pos:List[String]):Tree},Lock)) =>
              pair._2.available
          }
          if (validParser >= 0) { // case: [likely] found parser to run
            val (parser, lock) = parsers(validParser)
            lock.acquire
            val rtn = parser.parse(words, pos)
            lock.release
            rtn
          } else { Thread.sleep(1000); tryParse } // case: no parser found
        }
        tryParse
      }
    }
  }
}
