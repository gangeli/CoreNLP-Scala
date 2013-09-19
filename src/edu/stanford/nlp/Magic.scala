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


object Magic {
  import NLP._

  /*
   * Implicit Conversions
   */
  implicit def seq2nlpseq(seq:Seq[String]):Sentence = new Sentence(seq)
  implicit def string2nlpseq(gloss:String):Sentence = new Sentence(gloss)
  
  implicit def map2mapping[I,O,X](map:Map[I,X]):Mapping[I,O] = Mapping(map)
  
  implicit def seq2ensemble[I](seq:Seq[I=>Boolean]):Ensemble[I] = new Ensemble(seq, None)
  
  implicit def fn2optimizable(
        fn:Array[Double]=>Double):OptimizableFunction = {
    optimize.algorithm.toLowerCase match {
      case "lbfgs" => LBFGSOptimizableApproximateFunction(fn, None)
      case "braindead" => BraindeadGradientDescent(fn, None)
      case _ => throw new IllegalStateException("Unknown algorithm: " + optimize.algorithm)
    }
  }
  implicit def fnPair2optimizable(
        pair:(Array[Double]=>Double,Array[Double]=>Array[Double])):OptimizableFunction = {
    optimize.algorithm.toLowerCase match {
       case "lbfgs" => LBFGSOptimizableApproximateFunction(pair._1, Some(pair._2))
       case "braindead" => BraindeadGradientDescent(pair._1, Some(pair._2))
       case _ => throw new IllegalStateException("Unknown algorithm: " + optimize.algorithm)
    }
  }

  implicit def string2tokensregex(str:String):TokensRegex = new TokensRegex(str)
}
