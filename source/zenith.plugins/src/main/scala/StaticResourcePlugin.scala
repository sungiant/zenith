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

final class StaticResourcePlugin [Z[_]: Context] (index: String, resourcePaths: List[String]) extends Plugin [Z] ("Static Resource Plugin") {
override val requestFilters = new StaticResourceRequestFilter[Z] (index, resourcePaths) :: Nil
}

final class StaticResourceRequestFilter[Z[_]: Context] (val index: String, val resourcePaths: List[String])(implicit logger: Logger[Z]) extends RequestFilter[Z] ("static_resource_request_filter") { import logger._
private [this] case class FileHandle (path: String, url: java.net.URL, inputStream: java.io.InputStream)

private def getFileHandle (path: String): Option[FileHandle] = {
  path.endsWith("/") match {
    case true => None
    case false => (
      Try (getClass.getResource (path)).toOption,
      Try (getClass.getResourceAsStream (path)).toOption) match {
      case (Some (url), Some (inputStream)) => Some (FileHandle (path, url, inputStream))
      case _ => None
    }
  }
}

private def getBytes[Z[_]: Context](fileHandle: FileHandle): Z[Option[List[Byte]]] = Async[Z].future {
  try {
    val bis = new java.io.BufferedInputStream(fileHandle.inputStream)
    val data = Stream.continually (bis.read).takeWhile(-1 !=).map(_.toByte).toList
    bis.close ()
    Some (data)
  } catch { case _ : Throwable => None }
}

private def getBestPath (requestPath: String): Z[Option[String]] = {
  resourcePaths match {
    case Nil => Async[Z].success { None: Option[String] }
    case paths => for {
      _ <- log (ZENITH, DEBUG, s"Resource paths: " + resourcePaths.mkString(", ") + ".")
      possibilities <- Async[Z].success {
        val possiblePaths = paths.map (p => p + requestPath)
        requestPath.contains('.') match {
          case true => possiblePaths
          case false => possiblePaths ::: possiblePaths.flatMap (p => index.map (i => p + i)) ::: Nil
        }
      }
      _ <- log (ZENITH, DEBUG, s"Possible paths for resource: " + possibilities.mkString(", ") + ".")
      pathOpt <- Async[Z].success { possibilities.find (getFileHandle (_).isDefined) }
      _ <- log (ZENITH, DEBUG, s"Best path: " + pathOpt.getOrElse ("None") + ".")
    } yield pathOpt
  }
}

def run (httpRequest: HttpRequest): Z[HttpResponse Either HttpRequest] = for {
  _ <- log (ZENITH, DEBUG, s"Working out best path.")
  bestPathOpt <- getBestPath (httpRequest.path)
  result <- bestPathOpt match {
    case None => for {
      _ <- log (ZENITH, DEBUG, s"No static resource path configured.")
      result <- Async[Z].success[Option[HttpResponse]] (None)
    } yield result
    case Some (bestPath) => for {
      _ <- log (ZENITH, DEBUG, s"Looking for static resource: $bestPath")
      result <- getFileHandle (bestPath) match {
        case None => for {
          _ <- log (ZENITH, DEBUG, s"No suitable static resource found for: ${httpRequest.path}")
        } yield None: Option[HttpResponse]
        case Some (fileHandle) => for {
          _ <- log (ZENITH, DEBUG, s"Found static resource at PATH: ${fileHandle.path}, URL: ${fileHandle.url}")
          bytes <- getBytes[Z](fileHandle)
        } yield bytes match {
          case None | Some (Nil) => Some (HttpResponse.plain (404, "Not Found"))
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
