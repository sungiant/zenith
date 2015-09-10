package demo

import io.circe._, io.circe.generic.semiauto._

final case class IncidentMessageTranslation (content: String, locale: String, updated_at: String)
object IncidentMessageTranslation {
  implicit val je = deriveFor[IncidentMessageTranslation].encoder
  implicit val jd = deriveFor[IncidentMessageTranslation].decoder
}

final case class IncidentMessage (id: Long, author: String, content: String, severity: String, translations: List[IncidentMessageTranslation], created_at: String, updated_at: String)
object IncidentMessage {
  implicit val je = deriveFor[IncidentMessage].encoder
  implicit val jd = deriveFor[IncidentMessage].decoder
}

final case class Incident (id: Long, active: Boolean, created_at: String, updates: List[IncidentMessage])
object Incident {
  implicit val je = deriveFor[Incident].encoder
  implicit val jd = deriveFor[Incident].decoder
}

final case class ShardService (name: String, incidents: List[Incident], slug: String, status: String)
object ShardService {
  implicit val je = deriveFor[ShardService].encoder
  implicit val jd = deriveFor[ShardService].decoder
}

final case class ShardStatus (name: String, hostname: String, locales: List[String], slug: String, region_tag: Option[String], services: List[ShardService])
object ShardStatus {
  implicit val je = deriveFor[ShardStatus].encoder
  implicit val jd = deriveFor[ShardStatus].decoder
}

final case class Shard (name: String, hostname: String, locales: List[String], slug: String, region_tag: Option[String])
object Shard {
  implicit val je = deriveFor[Shard].encoder
  implicit val jd = deriveFor[Shard].decoder
}
