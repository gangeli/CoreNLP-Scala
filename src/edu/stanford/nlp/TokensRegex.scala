package edu.stanford.nlp;

import scala.collection.JavaConversions._

import edu.stanford.nlp.ling.CoreLabel
import edu.stanford.nlp.util.CoreMap
import edu.stanford.nlp.ling.tokensregex._

import NLPConfig._


case class TokensRegex(override val toString:String) {
  val pattern:TokenSequencePattern = TokenSequencePattern.compile(toString)


  def matches(input:Seq[CoreLabel]):Boolean = pattern.getMatcher(input.toList).matches

  def allMatches(input:Seq[CoreLabel]):Iterator[Seq[CoreLabel]] = {
    val matcher = pattern.getMatcher(input.toList)
    new Iterator[Seq[CoreLabel]] {
      var theNext:Option[Boolean] = None
      override def hasNext:Boolean = theNext match {
        case Some(x) => x
        case None => theNext = Some(matcher.find); theNext.get
      }
      override def next:Seq[CoreLabel] = {
        if (!hasNext) throw new NoSuchElementException
        theNext = None
        val m:java.util.List[_ <: CoreMap] = matcher.groupNodes
        m.map( _ match {
          case (x:CoreLabel) => x
          case (x:CoreMap) => new CoreLabel(x)
        })
      }
    }
  }

  def unapplySeq(target:Any):Option[Seq[Seq[CoreLabel]]] = target match {
    case (input:Seq[CoreLabel]) =>
      val matcher = pattern getMatcher(input toList)
      if (matcher matches) {
        Some(for (i <- 1 to matcher.groupCount) yield 
          matcher groupNodes(i) map( _ match {
          case (x:CoreLabel) => x
          case (x:CoreMap) => new CoreLabel(x)
        }))
      } else { None }
    case _ => None
  }
}


object TokensRegex {
  // Built-in predicates
  def word(pattern:String):MarkedString = MarkedString(s"""{word : /$pattern/}""")
  def tag(pattern:String):MarkedString = MarkedString(s"""{tag : /$pattern/}""")
  def lemma(pattern:String):MarkedString = MarkedString(s"""{lemma : /$pattern/}""")
  def ner(pattern:String):MarkedString = MarkedString(s"""{ner : /$pattern/}""")
  def normalized(pattern:String):MarkedString = MarkedString(s"""{normalized : /$pattern/}""")

  // Decorate predicates
  case class MarkedString(str:String) extends AnyVal { override def toString:String = str }
  implicit def stringDecorator(str:MarkedString) = new {
    def unary_!():String = s"""!$str"""
  }
  implicit def string2string(str:MarkedString):String = str.str

  // Create token sequence
  implicit def product2tokens(p:Product):Tokens = new Tokens(List[String](p.productIterator.map( _.toString ).mkString(" & ")))
  implicit def string2tokens(str:MarkedString):Tokens = new Tokens(List[String](str.str))
  class Tokens(val regexps:List[String]) {
    def apply(terms:String*):Tokens = {
      new Tokens(terms.mkString(" & ") :: regexps)
    }
  }
  
  // Dump to TokensRegex object
  implicit def string2tokensregex(str:MarkedString):TokensRegex
    = new TokensRegex(s"""[${str.str}]""")
  implicit def tokens2tokensregex(tokens:Tokens):TokensRegex
    = new TokensRegex(s"""[${tokens.regexps.reverse.mkString("] [")}]""")
}
