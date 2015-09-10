package demo

final case class ProxyRequest (url: String, method: String)
object ProxyRequest {
  import io.circe._, io.circe.generic.semiauto._
  implicit val je = deriveFor[ProxyRequest].encoder
  implicit val jd = deriveFor[ProxyRequest].decoder
}