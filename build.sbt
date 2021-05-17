lazy val root = (project in file("."))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslServer,
      lagomScaladslPersistenceJdbc,
      lagomScaladslTestKit
    ) ++ Seq(
      Dependencies.playAkkaHttpServer,
      Dependencies.postgres,
      Dependencies.scalatest,
      Dependencies.testcontainersPostgresql,
      Dependencies.testcontainersScalaTest
    ).map(_ % Test),
    name := "lagom-scaladsl-persistence-jdbc-example"
  )
