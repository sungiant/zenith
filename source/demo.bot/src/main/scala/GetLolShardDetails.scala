package demo.bot

import demo._
import zenith._, zenith.bot._
import cats.data._

@description ("Get detailed shard info about a random specific shard from the LoL developer API.")
sealed class GetLolShardDetails[Z[_]: Context] (endpoint: String)
  extends ActionT [Z, RestClientState, ProxyRequest, ShardStatus] {
  import io.circe.jawn._, io.circe.syntax._
  val request: ReaderT[Z, RestClientState, ProxyRequest] = ReaderT { state =>
    state.regionID match {
      case None =>
        Async[Z].failure (new Exception ("FUCK: Unexpected client state, target region not set."))
      case Some (targetRegion) => Async[Z].success (ProxyRequest (s"http://status.leagueoflegends.com/shards/$targetRegion", "GET"))
    }
  }
  def requestMapper (p: ProxyRequest) = HttpRequest.createFromUrl (s"$endpoint/proxy", "POST", None, Some (p.asJson.noSpaces))
  def responseMapper (r: HttpResponse) = r.data.flatMap { d => decode[ShardStatus](d).toOption }

  @assertion
  @description ("Check that target region has no active incidents.")
  def regionOnline (response: ShardStatus): ReaderT[Z, RestClientState, Result] = ReaderT { _ =>
    Async[Z].success { if (response.services.flatMap(_.incidents).size == 0) Passed else Failed }
  }
}