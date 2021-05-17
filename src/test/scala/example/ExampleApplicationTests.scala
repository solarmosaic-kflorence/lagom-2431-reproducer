package example

import com.dimafeng.testcontainers.scalatest.TestContainersForAll
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.lightbend.lagom.scaladsl.api.{AdditionalConfiguration, ProvidesAdditionalConfiguration}
import com.lightbend.lagom.scaladsl.api.transport.NotFound
import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.ServiceTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import play.api.Configuration

class ExampleApplicationTests extends AsyncWordSpec with TestContainersForAll with Matchers {
  type Containers = PostgreSQLContainer

  def startContainers(): Containers = PostgreSQLContainer.Def().start()

  lazy val server: ServiceTest.TestServer[ExampleApplication] = withContainers { postgresContainer =>
    ServiceTest.startServer[ExampleApplication](ServiceTest.defaultSetup.withCluster.withJdbc) { context =>
      new ExampleApplication(context) with LocalServiceLocator with ProvidesAdditionalConfiguration {
        override def additionalConfiguration: AdditionalConfiguration =
          super.additionalConfiguration ++ Configuration(
            "db.default.url" -> postgresContainer.jdbcUrl,
            "db.default.username" -> postgresContainer.username,
            "db.default.password" -> postgresContainer.password
          ).underlying
      }
    }
  }

  lazy val client: Example = server.serviceClient.implement[Example]

  "get" should {
    "return failed future with NotFound for non-existing resource" in {
      client.get("test").invoke().failed.map { exception =>
        exception shouldBe a[NotFound]
      }
    }
    "return resource for existing resource" in {
      val name = "test"
      val data = Map("hello" -> "world")
      for {
        _ <- client.put(name).invoke(Request(data))
        result <- client.get(name).invoke
      } yield {
        result.name should be(name)
        result.data should be(data)
      }
    }
  }
}
