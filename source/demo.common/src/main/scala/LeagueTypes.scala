package demo.common

import io.circe._
import io.circe.generic.semiauto._

final case class IncidentMessageTranslation (content: String, locale: String, heading: String)
object IncidentMessageTranslation {
  implicit val je = deriveEncoder[IncidentMessageTranslation]
  implicit val jd = deriveDecoder[IncidentMessageTranslation]
}

final case class IncidentMessage (id: String, author: String, content: String, severity: String, translations: List[IncidentMessageTranslation], created_at: String, updated_at: String)
object IncidentMessage {
  implicit val je = deriveEncoder[IncidentMessage]
  implicit val jd = deriveDecoder[IncidentMessage]
}

final case class Incident (id: Long, active: Boolean, created_at: String, updates: List[IncidentMessage])
object Incident {
  implicit val je = deriveEncoder[Incident]
  implicit val jd = deriveDecoder[Incident]
}

final case class ShardService (name: String, incidents: List[Incident], slug: String, status: String)
object ShardService {
  implicit val je = deriveEncoder[ShardService]
  implicit val jd = deriveDecoder[ShardService]
}

final case class ShardStatus (name: String, hostname: String, locales: List[String], slug: String, region_tag: Option[String], services: List[ShardService])
object ShardStatus {
  implicit val je = deriveEncoder[ShardStatus]
  implicit val jd = deriveDecoder[ShardStatus]
}

final case class Shard (name: String, slug: String, locales: List[String], hostname: String, region_tag: Option[String])
object Shard {
  implicit val je = deriveEncoder[Shard]
  implicit val jd = deriveDecoder[Shard]
}
