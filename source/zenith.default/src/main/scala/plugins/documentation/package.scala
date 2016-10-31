/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith.default.plugins

import zenith._
import zenith.server._
import zenith.Logger._
import zenith.Logger.Level._
import zenith.Extensions._
import scala.util.{Try, Success, Failure}
import cats._
import cats.data._
import org.joda.time.{DateTimeZone, DateTime}
import cats.implicits._
import java.lang.reflect.{Method => ReflectedMethod}
import io.circe._, io.circe.generic.semiauto._

package object documentation {

  implicit val jeHttpMethod: Encoder[HttpMethod] = new Encoder[HttpMethod] { def apply (hm: HttpMethod): Json = { Json.fromString (hm.toString) } }
  implicit val jeEndpointAttributes = deriveEncoder[EndpointAttributes]
  implicit val jeServiceAttributes = deriveEncoder[ServiceAttributes]

  val LOGCH: Option[Logger.Channel] = Some ("DOCS_PLUGIN")

}
