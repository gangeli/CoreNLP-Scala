name := "CoreNLP-Scala"

organization := "com.github.gangeli"

scalaVersion := "2.11.6"

version := "1.0"

scalaSource in Compile := baseDirectory.value / "src"

excludeFilter in unmanagedSources := "Berkeley.scala"

libraryDependencies ++= Seq(
  // "edu.berkeley.nlp" % "berkeleyparser" % "r32",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.4",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.4" classifier "models",
  "edu.stanford.nlp" % "stanford-parser" % "3.4"
)

crossScalaVersions := Seq("2.10.4", "2.11.6")

publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository")))
