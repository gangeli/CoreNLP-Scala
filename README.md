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

Useful helper functions:

    scala> import edu.stanford.nlp._
    scala> val s = Sentence("NLP is Awesome!")
    scala> s.namedEntities
    res0: Array[(Array[String], String)] = Array((Array(NLP),ORGANIZATION))
    scala> s.head
    res1: Int = 1
    scala> s.headWord // or, s.word(s.head)
    res2: String = is

Magic!

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
    scala> val sentimentAnalyzer = Map( "Good plot" -> true, "Good acting" -> true, "Bad plot" -> false, "Bad experience" -> false ).classifier
    scala> sentimentAnalyzer.classify("Good movie")
    res0: O = true
    scala> sentimentAnalyzer.classify("Bad movie")
    res1: O = false
    
    
    
    
