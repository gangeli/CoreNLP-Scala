CoreNLP-Scala
=============

A Scala wrapper for CoreNLP, providing an easy to use, object-oriented
framework for many of the major CoreNLP components.

The main features include:

* Object-orientedness. Getting the parse tree of a sentence `s` is `s.parse`.
  The words are `s.words`, the parts of speech are `s.pos`, etc.
* Lazy evaluation. If you never want the parse tree of a sentence, it will never compute it.
  However, once a computation is complete, it will be cached for fast future retrieval.
* Compact API. Since the wrapper is intended to be read-only, arrays are used where possible,
  and often helper functions are provided (e.g., `sentence.headword`).
* Magic! Implicit conversions from "real-life" representations of sentences, etc. to
  make scripting easier.

Note that this is not intended to be a complete mirror of CoreNLP functionality
(https://github.com/sistanlp/processors is a more comprehensive wrapper), but rather
a lightweight utility for when you just want `import NLP` to work without leveraging
the entire CoreNLP infrastructure.

Example Usage
-------------

Create a sentence:

```scala
import edu.stanford.nlp._
val s = Sentence("NLP is Awesome!")

// yields: NLP :: is :: awesome :: !
println(s.words.mkString(" :: "))

// loads POS Tagger and Parser
// yields: (ROOT (S (NP (NNP NLP)) (VP (VBZ is) (ADJP (JJ awesome))) (. !)))
println(s.parse.toString)

// loads NER Tagger
// yields: ORGANIZATION :: O :: O :: O
println(s.ner.mkString(" :: "))

// re-uses Parser
// yields Array[(Int, String)] = Array((2,nsubj), (2,cop), (-1,root), (3,noop))
println(s.stanfordDependencies)
```

Useful helper functions:

```scala
scala> import edu.stanford.nlp._
scala> val s = Sentence("NLP is Awesome!")
scala> s.namedEntities
res0: Array[(Array[String], String)] = Array((Array(NLP),ORGANIZATION))
scala> s.headIndex
res1: Int = 1
scala> s.headWord // or, s.word(s.headIndex)
res2: String = is
```

Magic!

```scala
scala> import edu.stanford.nlp.Magic._

// Implicit conversions from String, Seq[String], Array[String]
scala> "NLP is awesome!".parse
res0: edu.stanford.nlp.trees.Tree = (ROOT (S (NP (NNP NLP)) (VP (VBZ is) (ADJP (JJ awesome))) (. !)))

// Optimize a [convex] function: (x_0 - 1)^2 + (x_1 - 2)^2
// Computes an analytic derivative if none given, or you can provide a derivative with .derivative()
// See Optimize.scala
scala> ((x:Array[Double]) => (x(0) - 1) * (x(0) - 1) + (x(1) - 2) * (x(1) - 2)).minimize(Array(0,0))
res1: Array[Double] = Array(0.999994999983933, 1.9999949999682796)

// Build a simple classifier (with some trivial NLP features)
// Not really to be taken seriously, but kind of fun nonetheless
scala> val sentimentData = Map( "Good plot" -> true, "Good acting" -> true, "Bad plot" -> false, "Bad experience" -> false )
scala> val sentimentAnalyzer = sentimentData.classifier
scala> sentimentAnalyzer("Good movie")
res0: O = true
scala> sentimentAnalyzer("Bad movie")
res1: O = false
scala> sentimentAnalyzer("bad movie")
res2: O = false
scala> sentimentAnalyzer("good experience")
res3: O = true

// This also works for Sentences, and for arbitrary output
// In that case, other features like POS, lemma, NER, etc. are also included, making for an almost reasonable baseline
scala> import edu.stanford.nlp._
scala> val spamData = Map( Sentence("discount credit!!! omg awesome!") -> 'spam, Sentence("your Stanford account") -> 'ham, Sentence("Nigerian prince") -> 'spam, Sentence("Chris Manning") -> 'ham )
scala> val spamClassifier = spamData.classifier
scala> spamClassifier("Buy stuff!!")
res0: O = 'spam
scala> spamClassifier("email from Chris")
res0: O = 'ham
scala> spamClassifier("John")
res0: O = 'ham  // PER tag -> ham
```

In-Depth: TokensRegex
---------------------
The wrapper provides a Scala-like interface to TokensRegex, in addition to
  a small domain specific language for a small subset of the syntax.
To create a TokensRegex pattern, you can follow code as below:

```scala
import edu.stanford.nlp._
import edu.stanford.nlp.TokensRegex
val Regex = TokensRegex("""[ { word:/Stanford/ } ] ([ { tag:/NNP/ }])""")

// matches() returns true if the entire sentence matches
Regex matches Sentence("Stanford CS") 

// allMatches() returns all matches for a regex in the sentence
for (result <- Regex allMatches Sentence("Stanford NLP is part of Stanford CS")) {
  println(result)  // prints List(Stanford, NLP) and List(Stanford, CS)
}

// Pattern matching
val Regex(subdepartment) = Sentence("Stanford NLP")
println(subdepartment)  // prints List(NLP)

// ...or...
Sentence("Stanford NLP") match {
  case Regex(subdepartment) =>
    println(subdepartment)  // reaches here; prints List(NLP)
  case _ =>
    println("NO MATCH")  // would reach here if not an exact match
}
```

Of course, the usual magic is still valid as well:

```scala
import edu.stanford.nlp.TokensRegex
import edu.stanford.nlp.Magic._

// note: String -> Sentence can't be implicitly converted, else String.matches(String) is invoked
"""[ { word:/Stanford/ } ] ([ { tag:/NNP/ }])""" matches ( Sentence("Stanford NLP") )
```

In addition, a small domain specific language can help make some compile-time
  checks of simple regular expressions.
In the language, every token is denoted in parentheses `( )`, every term
  in the parentheses is a comma separated list of conjunctive criteria
  (e.g., word is `X` and tag is `Y`), and multiple tokens are simply
  concatenated with each other.
To illustrate:

```scala
import edu.stanford.nlp._
import edu.stanford.nlp.TokensRegex._

val Regex = ( word("Stanford") ) ( word("[A-Z].*"), tag("NNP") )
Regex matches Sentence("Stanford CS")  // return true
```
