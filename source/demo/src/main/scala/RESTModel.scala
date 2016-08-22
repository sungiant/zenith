package demo

final case class ProxyRequest (url: String, method: String)
object ProxyRequest {
  import io.circe._, io.circe.generic.semiauto._
  implicit val je = deriveEncoder[ProxyRequest]
  implicit val jd = deriveDecoder[ProxyRequest]
}
