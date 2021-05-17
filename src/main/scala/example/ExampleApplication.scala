package example

import akka.NotUsed
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import akka.util.Timeout
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import com.lightbend.lagom.scaladsl.api.transport.{BadRequest, Method, NotFound}
import com.lightbend.lagom.scaladsl.persistence._
import com.lightbend.lagom.scaladsl.persistence.jdbc.JdbcPersistenceComponents
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import com.lightbend.lagom.scaladsl.server.{LagomApplication, LagomApplicationContext, LagomServer}
import play.api.db.HikariCPComponents
import play.api.libs.json.{Format, Json}
import play.api.libs.ws.ahc.AhcWSComponents

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.Try

abstract class ExampleApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
  with AhcWSComponents
  with HikariCPComponents
  with JdbcPersistenceComponents {
  lazy val jsonSerializerRegistry: JsonSerializerRegistry = Example.JsonSerializerRegistry
  lazy val lagomServer: LagomServer =
    serverFor[Example](new ExampleImpl(clusterSharding))
  val _ = clusterSharding.init(Entity(Example.Behavior.typeKey)(Example.Behavior.apply))
}

trait Example extends Service {
  def get(name: String): ServiceCall[NotUsed, Response]
  def put(name: String): ServiceCall[Request, Response]

  final override def descriptor: Descriptor = {
    Service
      .named(Example.name)
      .withAutoAcl(true)
      .withCalls(
        Service.restCall(Method.GET, "/resource/:name", get _),
        Service.restCall(Method.PUT, "/resource/:name", put _)
      )
  }
}

class ExampleImpl(clusterSharding: ClusterSharding)(
  implicit ec: ExecutionContext
) extends Example {
  implicit val timeout: Timeout = Timeout(5.seconds)

  protected[example] def entityRef(name: String): EntityRef[Example.Command] =
    clusterSharding.entityRefFor(Example.Behavior.typeKey, name)

  def get(name: String): ServiceCall[NotUsed, Response] =
    ServiceCall(
      _ =>
        entityRef(name).ask[Example.Reply](reply => Example.Get(reply)).map {
          case Example.Accepted(response) => response
          case Example.Rejected(reason) => throw NotFound(reason)
        }
    )

  def put(name: String): ServiceCall[Request, Response] =
    ServiceCall(
      request =>
        entityRef(name).ask[Example.Reply](reply => Example.Put(name, request.data, reply)).map {
          case Example.Accepted(response) => response
          case Example.Rejected(reason) => throw BadRequest(reason)
        }
    )
}

object Example {
  val name: String = "example"

  object Behavior {
    val typeKey: EntityTypeKey[Command] = EntityTypeKey(Example.name)

    def apply(persistenceId: PersistenceId): EventSourcedBehavior[Command, Event, State] =
      EventSourcedBehavior.withEnforcedReplies(
        persistenceId,
        emptyState = Empty,
        commandHandler = (state, command) => state.apply(command),
        eventHandler = (state, event) => state.apply(event)
      )

    def apply(entityContext: EntityContext[Command]): Behavior[Command] =
      apply(PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId))
        .withTagger(AkkaTaggerAdapter.fromLagom(entityContext, Event.Tag))
        .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2))
  }

  object JsonSerializerRegistry extends JsonSerializerRegistry {
    override def serializers: immutable.Seq[JsonSerializer[_]] = immutable.Seq(
      JsonSerializer[Updated]
    )
  }

  sealed trait Command extends JacksonJsonSerializable {
    val replyTo: ActorRef[Reply]
  }

  final case class Get(replyTo: ActorRef[Reply]) extends Command
  final case class Put(name: String, data: Map[String, String], replyTo: ActorRef[Reply]) extends Command

  sealed trait Reply extends JacksonJsonSerializable

  final case class Accepted(response: Response) extends Reply
  final case class Rejected(reason: String) extends Reply

  sealed trait Event extends AggregateEvent[Event] {
    override def aggregateTag: AggregateEventTagger[Event] = Event.Tag
  }

  object Event {
    val Tag: AggregateEventShards[Example.Event] = AggregateEventTag.sharded[Example.Event](numShards = 10)
  }

  final case class Updated(name: String, data: Map[String, String]) extends Event
  object Updated {
    implicit val format: Format[Updated] = Json.format
  }

  sealed trait State extends JacksonJsonSerializable {
    def apply(command: Command): ReplyEffect[Event, State] = command match {
      case Put(name, data, replyTo) =>
        Effect
          .persist[Event, State](Updated(name, data))
          .thenReply(replyTo)(_ => Accepted(Response(name, data)))
      case command => throw new MatchError(command)
    }
    def apply(event: Event): State = event match {
      case Updated(name, data) => Exists(name, data)
      case _ =>
        throw new MatchError(
          s"Event ${event.getClass.getSimpleName} is not supported in state ${getClass.getSimpleName}"
        )
    }
    def reject(command: Command): ReplyEffect[Event, State] =
      Effect.none.thenReply(command.replyTo)(
        _ => Rejected(s"Command ${command.getClass.getSimpleName} is not valid for state ${getClass.getSimpleName}.")
      )
  }

  case object Empty extends State {
    override def apply(command: Command): ReplyEffect[Event, State] =
      Try(super.apply(command)).getOrElse(reject(command))
  }

  final case class Exists(name: String, data: Map[String, String]) extends State {
    override def apply(command: Command): ReplyEffect[Event, State] =
      Try(super.apply(command)).getOrElse(command match {
        case Get(replyTo) => Effect.reply(replyTo)(Accepted(Response(name, data)))
        case command => reject(command)
      })
  }
}

/** Marker trait for Akka Jackson serialization.
  * @see https://doc.akka.io/docs/akka/2.6/serialization-jackson.html
  */
trait JacksonJsonSerializable

case class Request(data: Map[String, String])
object Request {
  implicit val format: Format[Request] = Json.format
}

case class Response(name: String, data: Map[String, String])
object Response {
  implicit val format: Format[Response] = Json.format
}
