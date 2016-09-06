/**
  *   __________            .__  __  .__
  *   \____    /____   ____ |__|/  |_|  |__
  *     /     // __ \ /    \|  \   __\  |  \
  *    /     /\  ___/|   |  \  ||  | |   Y  \
  *   /_______ \___  >___|  /__||__| |___|  /
  *           \/   \/     \/              \/
  */
package zenith.netty.test

import zenith._
import org.specs2.mutable._
import Extensions._
import zenith.netty._

class NettyUtilsSpec extends Specification {
  "Converting between Netty and Zenith types" should {
    "work correctly for HttpResponse" in {
      var zResp1 = HttpResponse.createJson (
        200,
        """[{"name":"North America","slug":"na","locales":["en_US"],"hostname":"prod.na1.lol.riotgames.com","region_tag":"na1"},{"name":"EU West","slug":"euw","locales":["en_GB","fr_FR","it_IT","es_ES","de_DE"],"hostname":"prod.euw1.lol.riotgames.com","region_tag":"eu"},{"name":"EU Nordic & East","slug":"eune","locales":["en_PL","hu_HU","pl_PL","ro_RO","cs_CZ","el_GR"],"hostname":"prod.eun1.lol.riotgames.com","region_tag":"eun1"},{"name":"Latin America North","slug":"lan","locales":["es_MX"],"hostname":"prod.la1.lol.riotgames.com","region_tag":"la1"},{"name":"Latin America South","slug":"las","locales":["es_MX"],"hostname":"prod.la2.lol.riotgames.com","region_tag":"la2"},{"name":"Brazil","slug":"br","locales":["pt_BR"],"hostname":"prod.br.lol.riotgames.com","region_tag":"br1"},{"name":"Russia","slug":"ru","locales":["ru_RU"],"hostname":"prod.ru.lol.riotgames.com","region_tag":"ru1"},{"name":"Turkey","slug":"tr","locales":["tr_TR"],"hostname":"prod.tr.lol.riotgames.com","region_tag":"tr1"},{"name":"Oceania","slug":"oce","locales":["en_AU"],"hostname":"prod.oc1.lol.riotgames.com","region_tag":"oc1"},{"name":"Republic of Korea","slug":"kr","locales":["ko_KR"],"hostname":"prod.kr.lol.riotgames.com","region_tag":"kr1"},{"name":"Japan","slug":"jp","locales":["ja_JP"],"hostname":"prod.jp1.lol.riotgames.com","region_tag":"jp1"}]""",
        Map (
          "Cache-Control" -> "public, max-age=2",
          "X-Cache-Hits" -> "1",
          "Access-Control-Allow-Origin" -> "*"))

      var nResp = NettyUtils.toNetty (zResp1)
      var zResp2 = NettyUtils.toZenith (nResp)

      zResp1.data must_== zResp2.data
      zResp1.code must_== zResp2.code
      
      zResp1.headers.foreach { case (k, v) =>
        zResp2.headers.contains (k) must_== true
        zResp2.headers (k) must_== v
      }

      zResp1.body must_== zResp2.body
    }
    "work correctly for HttpRequest" in {
      var zReq1 = HttpRequest.createJson (
        "https://www.google.co.uk",
        "PUT",
        "{}",
        Map ("Foo" -> "Bar"))

      var nReq = NettyUtils.toNetty (zReq1)
      var zReq2 = NettyUtils.toZenith (nReq)

      zReq1.data must_== zReq2.data
      zReq1.method must_== zReq2.method
      zReq1.requestUri must_== zReq2.requestUri
      zReq1.version must_== zReq2.version

      // TODO: Enable these...
      //zReq1.host must_== zReq2.host
      //zReq1.hostPort must_== zReq2.hostPort
      
      zReq1.headers.foreach { case (k, v) =>
        zReq2.headers.contains (k) must_== true
        zReq2.headers (k) must_== v
      }

      zReq1.body must_== zReq2.body
    }
  }
}