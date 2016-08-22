package demo.bot

import demo._
import zenith._, zenith.bot._
import cats.data._

import scala.util.{Success, Failure, Try}

@description ("Get detailed shard info about a random specific shard from the LoL developer API.")
sealed class GetLolShardDetails[Z[_]: Context] (endpoint: String)
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
    HttpRequest.createFromUrl (s"$endpoint/proxy", "POST", None, p.asJson.noSpaces.getBytes (utf8).toList)
  }}

  def responseMapper: ReaderT[Z, HttpResponse, Try[ShardStatus]] = ReaderT { r => Async[Z].success {
    r.body match {
      case Some (d) => decode[ShardStatus](d).toEither match {
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
