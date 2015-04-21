name := "CoreNLP-Scala"

organization := "CoreNLP-Scala"

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
  // "edu.berkeley.nlp" % "berkeleyparser" % "r32",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.4",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.4" classifier "models",
  "edu.stanford.nlp" % "stanford-parser" % "3.4"
)

