/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith.server.plugins

import zenith._
import zenith.Context
import zenith.server._
import zenith.Logger._
import zenith.Logger.Level._
import zenith.Extensions._
import scala.util.{Try, Success, Failure}
import cats._
import cats.data._
import org.joda.time.{DateTimeZone, DateTime}
import cats.Monad.ops._
import java.lang.reflect.{Method => ReflectedMethod}
import io.circe._, io.circe.generic.semiauto._

package object documentation {

    implicit val jeHttpMethod: Encoder[HttpMethod] = new Encoder[HttpMethod] { def apply (hm: HttpMethod): Json = { Json.fromString (hm.toString) } }
    implicit val jeEndpointAttributes = deriveEncoder[EndpointAttributes]
    implicit val jeServiceAttributes = deriveEncoder[ServiceAttributes]

  /*
   * A dead simple plugin that when registered just adds a new services to the app.
   * The service has one endpoint that provides documentation, gleaned from the server config, about the server.
   */
  final class DocumentationPlugin [Z[_]: Context] (config: () => HttpServerConfig [Z]) extends Plugin [Z] ("Documentation Plugin")  {
    override val services = new DocumentationService[Z] (config) :: Nil
  }

  final case class Documentation (id: String, services: List[ServiceAttributes])
  object Documentation { implicit val je = deriveEncoder[Documentation] }

  final class DocumentationService[Z[_]: Context] (config: () => HttpServerConfig[Z]) extends Service[Z] {

    @endpoint
    @path ("^/documentation$")
    @method (HttpMethod.GET)
    @description ("Provides documentation for the API.")
    def docs (request: HttpRequest): Z[HttpResponse] = {
      import io.circe.syntax._
      val c = config ()
      Async[Z].success (HttpResponse.json (200, Documentation (c.identifier, c.services.map (_.attributes)).asJson.noSpaces))
    }
  }
}
