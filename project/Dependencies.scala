import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.15"

  lazy val kubernetesClient =
    "com.goyeau" %% "kubernetes-client" % "0.9.0"

  lazy val circeVersion = "0.14.3"
  lazy val circeExtra = "io.circe" %% "circe-generic-extras" % circeVersion
  lazy val circeCore = "io.circe" %% "circe-core" % circeVersion

  lazy val logbackClassic =
    "ch.qos.logback" % "logback-classic" % "1.4.7"

  val pureConfigVersion = "0.17.4"
  lazy val pureConfig =
    "com.github.pureconfig" %% "pureconfig" % pureConfigVersion

  val betterMonadicVersion = "0.3.1"
  lazy val betterMonadicFor =
    "com.olegpy" %% "better-monadic-for" % betterMonadicVersion
}
