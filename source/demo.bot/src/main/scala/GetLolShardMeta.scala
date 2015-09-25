package demo.bot

import demo._
import zenith._, zenith.bot._
import cats.data._

@description ("Get high level shard info from the LoL developer API.")
sealed class GetLolShardMeta[Z[_]: Context] (endpoint: String, shardName: String)
  extends ActionT [Z, RestClientState, ProxyRequest, List[Shard]] {
  import io.circe.jawn._, io.circe.syntax._

  private val utf8 = java.nio.charset.Charset.forName ("UTF-8")

  val request: ReaderT[Z, RestClientState, ProxyRequest] = ReaderT { _ =>
    Async[Z].success (ProxyRequest ("http://status.leagueoflegends.com/shards", "GET"))
  }
  def requestMapper (p: ProxyRequest) = HttpRequest.createFromUrl (s"$endpoint/proxy", "POST", None, p.asJson.noSpaces.getBytes (utf8).toList)
  def responseMapper (r: HttpResponse) = r.body.flatMap { d => decode[List[Shard]](d).toOption }

  @assertion
  @description ("Check that shardName is a known shard.")
  def shardKnown (response: List[Shard]): ReaderT[Z, RestClientState, Result] = ReaderT { _ =>
    Async[Z].success { if (response.map(_.name).contains(shardName)) Passed else Failed }
  }

  override def after = Option { (dcs: RestClientState, response: List[Shard]) =>
    val regionID = response.find (x => x.name == shardName).flatMap (x => x.region_tag)
    dcs.copy (regionID = regionID)
  }
}