/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith.default.plugins.documentation

import zenith._
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
import zenith.default._

/*
 * A dead simple plugin that when registered just adds a new services to the app.
 * The service has one endpoint that provides documentation, gleaned from the server config, about the server.
 */
final class DocumentationPlugin [Z[_]: Monad: Async: Logger](config: () => HttpServerConfig [Z])
extends HttpServerPlugin [Z] ("Documentation Plugin")  {

  override val services = new DocumentationService[Z] (config) :: Nil
}

final case class Documentation (id: String, services: List[ServiceAttributes])
object Documentation { implicit val je = deriveEncoder[Documentation] }

final class DocumentationService[Z[_]: Monad: Async: Logger](config: () => HttpServerConfig[Z]) extends Service[Z] {

  @endpoint
  @path ("^/documentation$")
  @method (HttpMethod.GET)
  @description ("Provides documentation for the API.")
  def docs (request: HttpRequest): Z[HttpResponse] = for {
    json <- Async[Z].future {
      import io.circe.syntax._
      val c = config ()
      val docs = Documentation (c.identifier, c.services.map (_.attributes))
      docs.asJson.noSpaces
    }
    _ <- Logger[Z].log (LOGCH, DEBUG, s"Successfully generated documentation for server.")
  } yield HttpResponse.createJson (200, json)
}
