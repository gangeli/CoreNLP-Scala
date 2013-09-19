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

// ----------
// Classifiers
// ----------
@SerialVersionUID(1l)
class Classifier[I,O](
     regression:I=>Map[O,Double],
     val data:Map[I,(O,Float)]) extends Function1[I,O] with Serializable {
  override def apply(in:I):O = {
    regression(in).maxBy(_._2)._1
  }
}

class Mapping[I,O](map:Map[I,(O,Float)]) {
  import Mapping.{toCounter,defaultFeatures}

  def scorer[F](featurizer:I=>Iterable[F]):I=>Map[O,Double] = {
    // -- Create Dataset
    val weights = new Array[Float](map.size)
    val dataset = new RVFDataset[O, F](map.size)
    map.zipWithIndex.foreach{
        case ((input:I, (output:O, weight:Float)),i:Int) =>
      weights(i) = weight
      dataset.add( new RVFDatum[O, F](toCounter(featurizer(input)), output) )
    }
    // -- Train
    val prior = new LogPrior(LogPrior.LogPriorType.QUADRATIC)
    val factory = new LinearClassifierFactory[O,F]()
    val classifier = factory.trainClassifier(dataset, weights, prior)
    // -- Return
    (input:I) => {
      val scores = classifier.scoresOf(
        new RVFDatum[O, F](toCounter(featurizer(input)), null.asInstanceOf[O]))
      scores.keySet.map{ x => (x, scores.getCount(x)) }.toMap
    }
  }
  def scorer:I=>Map[O,Double] = scorer(defaultFeatures(_, map.size))

  def classifier[F](featurizer:I=>Iterable[F]):Classifier[I,O]
    = new Classifier(scorer(featurizer), map)
  def classifier:Classifier[I,O]
    = classifier(defaultFeatures(_, map.size))
}

object Mapping {
  def toCounter[X,F](map:Iterable[X]):Counter[F] = {
    val counts = new ClassicCounter[F]
    map.foreach{ (x:X) => x match {
      case (feat:F, n:Number) => counts.incrementCount(feat, n.doubleValue)
      case (feat:F) => counts.incrementCount(feat, 1)
      case _ => throw new IllegalStateException("Type mismatch in toCounter")
    } }
    return counts
  }
  
  def apply[I,O,X](map:Map[I,X]):Mapping[I,O] = {
    new Mapping(map.map{ case (i:I, x:X) => x match {
      case (o:O, n:Number) => (i, (o, n.floatValue))
      case (o:O) => (i, (o, 1.0.asInstanceOf[Float]))
      case _ => throw new IllegalStateException("Type mismatch in toCounter")
    } })
  }

  def defaultFeatures[I](input:I, datasetSize:Int):Iterable[(String,Float)] = {
    def ngram[A](seq:List[A], n:Int, tail:List[A] = Nil):List[String] = {
      if (seq.isEmpty) Nil
      else (seq.head :: tail.slice(0, n-1)).reverse.mkString("_") :: ngram(seq.tail, n, seq.head :: tail)
    }
    input match {
      case (sent:Sentence) =>
        val n:Int = (scala.math.log10(datasetSize) / 3.0).toInt + 1
        // N-grams
        (ngram(sent.words.toList, n) :::
         ngram(sent.words.toList.map( _.toLowerCase ), n) :::
         ngram(sent.lemma.toList, n) :::
         ngram(sent.ner.toList, n) :::
         ngram(sent.pos.toList, n) :::
         // Bag-of-words
         { if (n > 1)
            sent.words.toList :::
            sent.words.toList.map( _.toLowerCase ) :::
            sent.lemma.toList :::
            sent.ner.toList :::
            sent.pos.toList
           else Nil }
        ).map{ (_, 1.0.toFloat) }
      case (str:String) =>
        val tokens = str.split(" ")
        val n:Int = (scala.math.log10(datasetSize) / 3.0).toInt + 1
        if (tokens.length <= 1) {
          // Case: a single word
          (tokens(0) ::  // memorize
            ngram(str.toCharArray.toList, n) :::  // literal n-grams
            ngram(str.toLowerCase.toCharArray.toList, n)  // case-insensitive n-grams
            ).map{ (_, 1.0.toFloat) }
        } else {
          // Case: a phrase
          (ngram(tokens.toList, n) :::  // literal n-grams
           ngram(tokens.toList.map( _.toLowerCase), n)  // case-insensitive n-grams
          ).map{ (_, 1.0.toFloat) }
        }
      case (seq:Iterable[Any]) =>
      seq.map{ (x:Any) => x match {
        case (feat:Any, n:Number) => (feat.toString, n.floatValue)
        case (feat:Any) => (feat.toString, 1.0.toFloat)
        case _ => (x.toString, 1.0.toFloat)
      } }
      case _ => List[(String,Float)]( (input.toString, 1.0.toFloat) )
    }
  }
}

// ----------
// Ensemble Classifiers
// ----------

class Ensemble[I](members:Seq[I=>Boolean], dat:Option[Map[I,(Boolean,Float)]]) {
  // -- Get Data
  if (!dat.isDefined) {
    members.foldLeft(Option[Map[I,(Boolean,Float)]](null)){
        (dat:Option[Map[I,(Boolean,Float)]], fn:I=>Boolean) =>
      fn match {
        case (classifier:Classifier[I,Boolean]) =>
          dat match {
            case Some(existingData) =>
              if (classifier.data != existingData) {
                warn("Classifiers trained on different data; taking union")
                Some(classifier.data ++ existingData)
              } else {
                Some(existingData)
              }
            case None => Some(classifier.data)
          }
        case _ => dat
      }
    }
  }

  // -- Methods
  def data(d:Map[I,(Boolean,Float)]):Ensemble[I] = new Ensemble(members, Some(d))
  def data(d:Seq[(I,Boolean)]):Ensemble[I]
    = data( d.map( x => (x._1, (x._2, 1.0f)) ).toMap )
  
  /**
   *  Implementation of AdaBoost.
   *  Taken from http://en.wikipedia.org/wiki/AdaBoost
   */
  def boost(data:Map[I,(Boolean,Float)]):Classifier[I,Boolean] = {
    if (data.isEmpty) throw new IllegalArgumentException("No data to train on!")
    // -- Cache
    startTrack("Running Weak Learners")
    val dataAsArray = data.toArray
    val gold = dataAsArray.map( _._2._1 )
    val predictions:Array[(I=>Boolean,Array[(Boolean, Float)])]
      = members.toList.par.map{ (h:I=>Boolean) =>
        log("running " + h.toString)
        (h, dataAsArray.map{ case (in:I, (out:Boolean, weight:Float)) =>
          (h(in), weight)
        })
      }.toArray
    endTrack("Running Weak Learners")
    // -- Error Rate
    def error(predictions:Array[(Boolean,Float)],
              gold:Array[Boolean],
              d:Array[Double] = (0 until data.size).map( x => 1.0 / data.size ).toArray
              ):Double = {
      predictions.zip(gold).zip(d).foldLeft(0.0){
          case (sum:Double,
               (( (guess:Boolean, weight:Float),
                gold:Boolean),
                di:Double)) =>
        if(guess == gold) sum else sum + di * weight
      }
    }
    def regressor(coefficients:Seq[(Double, I=>Boolean)]
                  ):(I => Map[Boolean, Double]) = (in:I) => {
      val sum = coefficients.foldLeft(0.0){
          case (sum:Double, (alpha:Double, h:(I=>Boolean))) =>
        sum + alpha * { if(h(in)) 1.0 else -1.0 }
      }
      Map[Boolean, Double]( true  -> {if(sum >= 0.0) 1.0 else 0.0 },
                            false -> {if(sum >= 0.0) 0.0 else 1.0 } )
    }
    // -- Run an Iteration
    def iter(t:Int,
             predictions:Array[(I=>Boolean, Array[(Boolean,Float)])],
             gold:Array[Boolean],
             soFar:List[(Double, I=>Boolean)],
             d:Array[Double] = data.map( x => 1.0 / data.size.toDouble ).toArray,
             tolerance:Double = NLPConfig.classify.tolerance
             ):List[(Double, I=>Boolean)] = {
      startTrack("Iteration " + t)
      // (get errors)
      val errors = predictions.map{ case (h, pred:Array[(Boolean,Float)]) =>
        ( h, pred, error(pred, gold, d) )
      }
      val (hOpt, predOpt, et) = errors.maxBy( x => scala.math.abs(0.5 - x._3) )
      // (compute update)
      log("optimal classifier: " + hOpt)
      log("e_t: " + et)
      val at   = 0.5 * scala.math.log( (1.0 - et) / et )
      val newD = predOpt.zip(gold).zip(d).map{
          case (((guess:Boolean, weight:Float), gold:Boolean), di:Double) =>
        di * scala.math.exp(- {if (guess == gold) 1.0 else -1.0} * at)
      }
      val sumD = newD.sum
      for (i <- 0 until newD.length) { newD(i) /= sumD }
      // (update coefficients)
      val coeffs = (at, hOpt) :: soFar
      log("a_t: " + at)
      endTrack("Iteration " + t)
      // (recurse)
      if ( scala.math.abs(0.5 - et) < tolerance ||
           t >= NLPConfig.classify.iterations) {
        coeffs
      } else {
        iter(t+1, predictions, gold, coeffs, newD, tolerance)
      }
    }
    // -- Construct Classifier
    startTrack("Boosting over " + members.length + " classifier and " + data.size + " examples")
    val fn = regressor(iter(1, predictions, gold, Nil))
    endTrack("Boosting over " + members.length + " classifier and " + data.size + " examples")
    new Classifier(fn, data)
  }

  def boost:Classifier[I,Boolean]
    = boost(dat.getOrElse(Map[I,(Boolean,Float)]()))
}
