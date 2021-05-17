import com.lightbend.lagom.core.LagomVersion
import sbt._

object Dependencies {
  val playAkkaHttpServer = "com.typesafe.play" %% "play-akka-http-server" % LagomVersion.play
  val postgres = "org.postgresql" % "postgresql" % "42.2.8"
  val scalatest = "org.scalatest" %% "scalatest" % "3.2.2"
  val testcontainersPostgresql = "com.dimafeng" %% "testcontainers-scala-postgresql" % Versions.testcontainers
  val testcontainersScalaTest = "com.dimafeng" %% "testcontainers-scala-scalatest" % Versions.testcontainers
}

object Versions {
  val testcontainers = "0.38.8"
}
