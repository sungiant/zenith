package demo.bot

import demo.common._
import zenith._, zenith.bot._
import cats.Monad
import cats.data._

import scala.util.{Success, Try, Failure}

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
        decode[List[Shard]](d).toEither match {
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
