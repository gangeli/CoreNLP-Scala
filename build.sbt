name := "CoreNLP-Scala"

organization := "CoreNLP-Scala"

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
  "edu.stanford.nlp" % "stanford-corenlp" % "3.3.0" artifacts(Artifact("stanford-corenlp", "models"), Artifact("stanford-corenlp")),
  "org.scalatest" % "scalatest_2.10" % "2.1.0" % "test",
  "edu.berkeley.nlp" % "berkeleyparser" % "r32"
)
