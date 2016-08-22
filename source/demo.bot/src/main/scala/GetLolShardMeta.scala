package demo.bot

import demo._
import zenith._, zenith.bot._
import cats.data._

import scala.util.{Success, Try, Failure}

@description ("Get high level shard info from the LoL developer API.")
sealed class GetLolShardMeta[Z[_]: Context] (endpoint: String, shardName: String)
  extends ActionT [Z, RestClientState, ProxyRequest, List[Shard]] {
  import io.circe.jawn._, io.circe.syntax._

  private val utf8 = java.nio.charset.Charset.forName ("UTF-8")

  val request: ReaderT[Z, RestClientState, ProxyRequest] = ReaderT { _ =>
    Async[Z].success (ProxyRequest ("http://status.leagueoflegends.com/shards", "GET"))
  }

  def requestMapper: ReaderT[Z, ProxyRequest, HttpRequest] = ReaderT { p => Async[Z].success {
    HttpRequest.createFromUrl (s"$endpoint/proxy", "POST", None, p.asJson.noSpaces.getBytes (utf8).toList)
  }}

  def responseMapper: ReaderT[Z, HttpResponse, Try[List[Shard]]] = ReaderT { r => Async[Z].success {
    r.body match {
      case Some (d) =>
        val e = """[{"name":"North America","slug":"na","locales":["en_US"],"hostname":"prod.na1.lol.riotgames.com","region_tag":"na1"},{"name":"EU West","slug":"euw","locales":["en_GB","fr_FR","it_IT","es_ES","de_DE"],"hostname":"prod.euw1.lol.riotgames.com","region_tag":"eu"},{"name":"EU Nordic & East","slug":"eune","locales":["en_PL","hu_HU","pl_PL","ro_RO","cs_CZ","el_GR"],"hostname":"prod.eun1.lol.riotgames.com","region_tag":"eun1"},{"name":"Latin America North","slug":"lan","locales":["es_MX"],"hostname":"prod.la1.lol.riotgames.com","region_tag":"la1"},{"name":"Latin America South","slug":"las","locales":["es_MX"],"hostname":"prod.la2.lol.riotgames.com","region_tag":"la2"},{"name":"Brazil","slug":"br","locales":["pt_BR"],"hostname":"prod.br.lol.riotgames.com","region_tag":"br1"},{"name":"Russia","slug":"ru","locales":["ru_RU"],"hostname":"prod.ru.lol.riotgames.com","region_tag":"ru1"},{"name":"Turkey","slug":"tr","locales":["tr_TR"],"hostname":"prod.tr.lol.riotgames.com","region_tag":"tr1"},{"name":"Oceania","slug":"oce","locales":["en_AU"],"hostname":"prod.oc1.lol.riotgames.com","region_tag":"oc1"},{"name":"Republic of Korea","slug":"kr","locales":["ko_KR"],"hostname":"prod.kr.lol.riotgames.com","region_tag":"kr1"},{"name":"Japan","slug":"jp","locales":["ja_JP"],"hostname":"prod.jp1.lol.riotgames.com","region_tag":"jp1"}]"""
        decode[List[Shard]](e).toEither match {
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
