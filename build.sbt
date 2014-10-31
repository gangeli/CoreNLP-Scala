name := "CoreNLP-Scala"

version := "1.0"

scalaSource in Compile := baseDirectory.value / "src"

excludeFilter in unmanagedSources := "Berkeley.scala"

libraryDependencies ++= Seq(
  // "edu.berkeley.nlp" % "berkeleyparser" % "r32",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.4",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.4" classifier "models",
  "edu.stanford.nlp" % "stanford-parser" % "3.4"
)