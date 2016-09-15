package demo.common

import io.circe._
import io.circe.generic.semiauto._

final case class ProxyRequest (url: String, method: String)
object ProxyRequest {
  implicit val je = deriveEncoder[ProxyRequest]
  implicit val jd = deriveDecoder[ProxyRequest]
}
