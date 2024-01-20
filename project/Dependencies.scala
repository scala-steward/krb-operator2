import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.17"

  lazy val kubernetesClient =
    "com.goyeau" %% "kubernetes-client" % "0.11.0"

  lazy val circeVersion = "0.14.6"
  lazy val circeExtra = "io.circe" %% "circe-generic-extras" % "0.14.3"
  lazy val circeCore = "io.circe" %% "circe-core" % circeVersion

  lazy val logbackClassic =
    "ch.qos.logback" % "logback-classic" % "1.4.14"

  val pureConfigVersion = "0.17.5"
  lazy val pureConfig =
    "com.github.pureconfig" %% "pureconfig" % pureConfigVersion

  val betterMonadicVersion = "0.3.1"
  lazy val betterMonadicFor =
    "com.olegpy" %% "better-monadic-for" % betterMonadicVersion
}
