package demo.bot

import scala.util.{Try, Success, Failure}
import scala.collection.immutable.HashSet
import scala.concurrent.ExecutionContext.Implicits.global
import io.circe._, io.circe.generic.semiauto._
import zenith._, zenith.bot._, zenith.client._, zenith.netty._
import cats._, cats.data._, cats.implicits._

/**********************************************************************************************************************/

final case class IncidentMessageTranslation (content: String, locale: String, heading: String)
object IncidentMessageTranslation {
  implicit val je = deriveEncoder[IncidentMessageTranslation]
  implicit val jd = deriveDecoder[IncidentMessageTranslation]
}

final case class IncidentMessage (id: String, author: String, content: String, severity: String, translations: List[IncidentMessageTranslation], created_at: String, updated_at: String)
object IncidentMessage {
  implicit val je = deriveEncoder[IncidentMessage]
  implicit val jd = deriveDecoder[IncidentMessage]
}

final case class Incident (id: Long, active: Boolean, created_at: String, updates: List[IncidentMessage])
object Incident {
  implicit val je = deriveEncoder[Incident]
  implicit val jd = deriveDecoder[Incident]
}

final case class ShardService (name: String, incidents: List[Incident], slug: String, status: String)
object ShardService {
  implicit val je = deriveEncoder[ShardService]
  implicit val jd = deriveDecoder[ShardService]
}

final case class ShardStatus (name: String, hostname: String, locales: List[String], slug: String, region_tag: Option[String], services: List[ShardService])
object ShardStatus {
  implicit val je = deriveEncoder[ShardStatus]
  implicit val jd = deriveDecoder[ShardStatus]
}

final case class Shard (name: String, slug: String, locales: List[String], hostname: String, region_tag: Option[String])
object Shard {
  implicit val je = deriveEncoder[Shard]
  implicit val jd = deriveDecoder[Shard]
}

final case class RestClientState (regionID: Option[String] = None)

final case class ProxyRequest (url: String, method: String)
object ProxyRequest {
  implicit val je = deriveEncoder[ProxyRequest]
  implicit val jd = deriveDecoder[ProxyRequest]
}

/**********************************************************************************************************************/

@description ("Check that the server side status page is running as expected")
sealed class CheckStatus[Z[_]: Monad: Async: Logger] (endpoint: String)
  extends ActionT [Z, RestClientState, HttpRequest, HttpResponse] {
  val request: ReaderT[Z, RestClientState, HttpRequest] = ReaderT { _ =>
    Async[Z].success (HttpRequest.createPlain (s"$endpoint/status"))
  }
  def requestMapper: ReaderT[Z, HttpRequest, HttpRequest] = ReaderT { x => Async[Z].success { x } }
  def responseMapper: ReaderT[Z, HttpResponse, Try[HttpResponse]] = ReaderT { x => Async[Z].success { Success (x) } }

  @assertion
  @description ("check that the response code received is 200")
  def responseCode (response: HttpResponse): ReaderT[Z, RestClientState, Result] = ReaderT { _ =>
    Async[Z].success { if (response.code == 200) Passed else Failed }
  }

  @assertion
  @description ("`all good` message exists in response body")
  def allGood (response: HttpResponse): ReaderT[Z, RestClientState, Result] = ReaderT { _ =>
    Async[Z].success {
      response.body match {
        case Some (body) =>
          body.contains("all good @ ") match {
          case true => Passed
          case false => Failed
        }
        case None => Failed
      }
    }
  }
}

/**********************************************************************************************************************/

@description ("Get detailed shard info about a random specific shard from the LoL developer API.")
sealed class GetLolShardDetails[Z[_]: Monad: Async: Logger] (endpoint: String)
  extends ActionT [Z, RestClientState, ProxyRequest, ShardStatus] {
  import io.circe.jawn._, io.circe.syntax._
  private val utf8 = java.nio.charset.Charset.forName ("UTF-8")
  val request: ReaderT[Z, RestClientState, ProxyRequest] = ReaderT { state =>
    state.regionID match {
      case None => Async[Z].failure (new Exception ("Unexpected client state, target region not set."))
      case Some (targetRegion) => Async[Z].success (ProxyRequest (s"http://status.leagueoflegends.com/shards/$targetRegion", "GET"))
    }
  }

  def requestMapper: ReaderT[Z, ProxyRequest, HttpRequest] = ReaderT { p => Async[Z].success {
    HttpRequest.createPlain (s"$endpoint/proxy", "POST", p.asJson.noSpaces)
  }}

  def responseMapper: ReaderT[Z, HttpResponse, Try[ShardStatus]] = ReaderT { r => Async[Z].success {
    r.body match {
      case Some (d) => decode[ShardStatus](d) match {
        case Left (l) => Failure (new Throwable (l))
        case Right (r) => Success (r)
      }
      case None => Failure (new Throwable ("Unexpected empty body"))
    }
  }}

  @assertion
  @description ("Check that target region has no active incidents.")
  def regionOnline (response: ShardStatus): ReaderT[Z, RestClientState, Result] = ReaderT { _ =>
    Async[Z].success { if (response.services.flatMap(_.incidents).isEmpty) Passed else Failed }
  }
}

/**********************************************************************************************************************/

@description ("Get high level shard info from the LoL developer API.")
sealed class GetLolShardMeta[Z[_]: Monad: Async: Logger] (endpoint: String, shardName: String)
  extends ActionT [Z, RestClientState, ProxyRequest, List[Shard]] {
  import io.circe.jawn._, io.circe.syntax._

  private val utf8 = java.nio.charset.Charset.forName ("UTF-8")

  val request: ReaderT[Z, RestClientState, ProxyRequest] = ReaderT { _ =>
    Async[Z].success (ProxyRequest ("http://status.leagueoflegends.com/shards", "GET"))
  }

  def requestMapper: ReaderT[Z, ProxyRequest, HttpRequest] = ReaderT { p => Async[Z].success {
    HttpRequest.createPlain (s"$endpoint/proxy", "POST", p.asJson.noSpaces)
  }}

  def responseMapper: ReaderT[Z, HttpResponse, Try[List[Shard]]] = ReaderT { r => Async[Z].success {
    r.body match {
      case Some (d) =>
        decode[List[Shard]](d) match {
        case Left (f) => Failure (new Throwable (f))
        case Right (s) => Success (s)
      }
      case None => Failure (new Throwable ("Unexpected empty body"))
    }
  }}

  @assertion
  @description ("Check that shardName is a known shard.")
  def shardKnown (response: List[Shard]): ReaderT[Z, RestClientState, Result] = ReaderT { _ =>
    Async[Z].success {
      if (response.map(_.name).contains(shardName)) Passed
      else Failed
    }
  }

  override def after = Option { (dcs: RestClientState, response: List[Shard]) =>
    val regionID = response.find (x => x.name == shardName).flatMap (x => x.region_tag)
    val newDcs = dcs.copy (regionID = regionID)
    newDcs
  }
}

/**********************************************************************************************************************/

object DemoBot {
  type C[$] = default.context.Type[$]
  type B = (RestClientState, Result)

  implicit val t = default.context.typeclassImplementations ()

  val clientProvider = new NettyHttpClientProvider[C]
  val client = clientProvider.create (HttpClientConfig ())

  val bot = new Bot[C, RestClientState] {
    lazy val createStartState: RestClientState = RestClientState ()
    val endpoint = "http://127.0.0.1:7777"
    val actions: List [Action[C, RestClientState]] =
      new CheckStatus[C] (endpoint) ::
      new GetLolShardMeta[C] (endpoint, "EU West") ::
      new GetLolShardDetails[C] (endpoint) ::
      Nil
  }

  def main (args: Array[String]): Unit = Async[C].onComplete[Result, Unit] (bot.run (client)) {
    case Success (result) =>
      result match {
        case Failed => System.out.println ("Quality Bot says `Sort it out`")
        case Passed => System.out.println ("Quality Bot says `Lookin' good`")
      }
      sys.exit (0)
    case Failure (error) =>
      System.out.println (error)
      sys.exit (0)
  }
}
