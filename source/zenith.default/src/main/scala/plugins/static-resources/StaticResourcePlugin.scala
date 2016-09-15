/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith.default.plugins.static_resources

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
import zenith.default._
import io.circe._, io.circe.generic.semiauto._

final class StaticResourcePlugin [Z[_]: Monad: Async: Logger](index: String, resourcePaths: List[String])
  extends Plugin [Z] ("Static Resource Plugin") {
  override val requestFilters = new StaticResourceRequestFilter[Z] (index, resourcePaths) :: Nil
}

final class StaticResourceRequestFilter[Z[_]: Monad: Async: Logger](val index: String, val resourcePaths: List[String])
  extends RequestFilter[Z] ("static_resource_request_filter") { import StaticResourceUtils._
  
  def run (httpRequest: HttpRequest): Z[HttpResponse Either HttpRequest] = for {
    _ <- Logger[Z].log (ZENITH_DEFAULT, DEBUG, s"Working out best path.")
    bestPathOpt <- getBestPath[Z] (index, resourcePaths)(httpRequest.path)
    result <- bestPathOpt match {
      case None => for {
        _ <- Logger[Z].log (ZENITH_DEFAULT, DEBUG, s"No static resource path configured.")
        result <- Async[Z].success[Option[HttpResponse]] (None)
      } yield result
      case Some (bestPath) => for {
        _ <- Logger[Z].log (ZENITH_DEFAULT, DEBUG, s"Looking for static resource: $bestPath")
        result <- getFileHandle (bestPath) match {
          case None => for {
            _ <- Logger[Z].log (ZENITH_DEFAULT, DEBUG, s"No suitable static resource found for: ${httpRequest.path}")
          } yield None: Option[HttpResponse]
          case Some (fileHandle) => for {
            _ <- Logger[Z].log (ZENITH_DEFAULT, DEBUG, s"Found static resource at PATH: ${fileHandle.path}, URL: ${fileHandle.url}")
            bytes <- getBytes[Z] (fileHandle)
          } yield bytes match {
            case None | Some (Nil) => Some (HttpResponse.createPlain (404, "Not Found"))
            case Some (content) =>
              import java.text.SimpleDateFormat
              import java.util.Calendar
              import java.util.GregorianCalendar
              import java.util.Locale
              import java.util.TimeZone
              val HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz"
              val HTTP_CACHE_SECONDS = 60 * 60 * 3

              val time = new GregorianCalendar ()
              time.add(Calendar.SECOND, HTTP_CACHE_SECONDS)
              val dateFormatter = new SimpleDateFormat (HTTP_DATE_FORMAT, Locale.US)
              dateFormatter.setTimeZone (TimeZone.getDefault)
              val contentType = ContentType.guessFromPath (bestPath)

              val headers = ContentType.isPrintable (bestPath) match {
                case true => Map (
                  "Content-Type" -> contentType,
                  "Date" -> dateFormatter.format (time.getTime)
                )
                case false => Map (
                  "Content-Type" -> contentType,
                  "Cache-Control" -> s"max-age=$HTTP_CACHE_SECONDS",
                  "Expires" -> dateFormatter.format (time.getTime),
                  "Date" -> dateFormatter.format (time.getTime)
                )
              }
              Option (HttpResponse (200, content, headers))
          }
        }
      } yield result
    }
  } yield result match {
    case Some (response) => Left (response)
    case None => Right (httpRequest)
  }
}
