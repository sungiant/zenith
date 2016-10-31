/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith.default.plugins.fileserver

import zenith._
import zenith.server._
import zenith.Logger._
import zenith.Logger.Level._
import zenith.Extensions._
import scala.util.{Try, Success, Failure}
import cats._
import cats.data._
import cats.implicits._
import org.joda.time.{DateTimeZone, DateTime}
import java.lang.reflect.{Method => ReflectedMethod}
import zenith.default._
import io.circe._, io.circe.generic.semiauto._
import java.text.SimpleDateFormat
import java.util.{Calendar, GregorianCalendar, Locale, TimeZone}

final class FileServerPlugin [Z[_]: Monad: Async: Logger](
  getResourceFn: (String) => java.net.URL,
  getResourceAsStreamFn: (String) => java.io.InputStream,
  index: String,
  resourcePaths: List[String])
  extends HttpServerPlugin [Z] ("File Server Plugin") {
  lazy val loader = new UserResourceLoader () {
    def getResource (p: String) = getResourceFn (p)
    def getResourceAsStream (p: String) = getResourceAsStreamFn (p)
  }
  override val requestFilters = new FileServerRequestFilter[Z] (loader, index, resourcePaths) :: Nil
}

final class FileServerRequestFilter[Z[_]: Monad: Async: Logger](
  val loader: UserResourceLoader,
  val index: String,
  val resourcePaths: List[String])
  extends RequestFilter[Z] ("file_server_request_filter", ApplyRequestFilterRule.IfRequestHandlerNotFound) {

  def run (httpRequest: HttpRequest): Z[HttpResponse Either HttpRequest] = for {
    resourceOpt <- Resource.findResourceForRequest[Z] (httpRequest).run ((loader, index, resourcePaths))
    result <- resourceOpt match {
      case None => Async[Z].success[Option[HttpResponse]] (None)
      case Some (resource) => for {
        _ <- Logger[Z].log (LOGCH, DEBUG, s"Found static resource at PATH: ${resource.path}, URL: ${resource.url}")
        bytes <- Resource.getBytes[Z] (resource)
      } yield bytes match {
        case None | Some (Nil) => Some (HttpResponse.createPlain (404, "Not Found"))
        case Some (content) =>
          val HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz"
          val HTTP_CACHE_SECONDS = 60 * 60 * 3

          val time = new GregorianCalendar ()
          time.add(Calendar.SECOND, HTTP_CACHE_SECONDS)
          val dateFormatter = new SimpleDateFormat (HTTP_DATE_FORMAT, Locale.US)
          dateFormatter.setTimeZone (TimeZone.getDefault)
          val contentType = ContentType.guessFromPath (resource.path)

          val headers = ContentType.isPrintable (resource.path) match {
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
  } yield result match {
    case Some (response) => Left (response)
    case None => Right (httpRequest)
  }
}
