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

private [this] case class FileHandle (path: String, url: java.net.URL, inputStream: java.io.InputStream)

private [this] object StaticResourceUtils {

  def getBytes[F[_]: Async] (fileHandle: FileHandle): F[Option[List[Byte]]] = Async[F].future {
    try {
      val bis = new java.io.BufferedInputStream(fileHandle.inputStream)
      val data = Stream.continually (bis.read).takeWhile(-1 !=).map(_.toByte).toList
      bis.close ()
      Some (data)
    } catch { case _ : Throwable => None }
  }

  def getFileHandle (path: String): Option[FileHandle] = {
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

  def getBestPath[F[_]: Monad: Async: Logger] (index: String, resourcePaths: List[String])(requestPath: String): F[Option[String]] = {
    resourcePaths match {
      case Nil => Async[F].success { None: Option[String] }
      case paths => for {
        _ <- Logger[F].log (ZENITH_DEFAULT, DEBUG, s"Resource paths: " + resourcePaths.mkString(", ") + ".")
        possiblePaths = paths.map (p => p + requestPath)
        possibilities = requestPath.contains('.') match {
          case true => possiblePaths
          case false => possiblePaths ::: possiblePaths.flatMap (p => index.map (i => p + i)) ::: Nil
        }
        _ <- Logger[F].log (ZENITH_DEFAULT, DEBUG, s"Possible paths for resource: " + possibilities.mkString(", ") + ".")
        pathOpt <- Async[F].future { possibilities.find (getFileHandle (_).isDefined) }
        _ <- Logger[F].log (ZENITH_DEFAULT, DEBUG, s"Best path: " + pathOpt.getOrElse ("None") + ".")
      } yield pathOpt
    }
  }
}
