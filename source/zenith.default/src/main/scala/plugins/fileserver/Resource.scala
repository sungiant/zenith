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
import scala.util.{Try, Success, Failure}
import cats._
import cats.implicits._
import cats.data._
import org.joda.time.{DateTimeZone, DateTime}
import java.lang.reflect.{Method => ReflectedMethod}
import zenith.default._
import java.io.{InputStream, BufferedInputStream, File}
import java.net.URL

trait UserResourceLoader {
  def getResource (path: String): URL
  def getResourceAsStream (path: String): InputStream
}
case class Resource (path: String, url: URL, inputStream: InputStream)
object Resource {

  private def combinePath (path1: String, path2: String): String = new File (new File (path1), path2).getPath

  def getBytes[F[_]: Async] (res: Resource): F[Option[List[Byte]]] = Async[F].future {
    try {
      val bis = new BufferedInputStream(res.inputStream)
      val data = Stream.continually (bis.read).takeWhile(-1 !=).map(_.toByte).toList
      bis.close ()
      Some (data)
    } catch { case _ : Throwable => None: Option[List[Byte]] }
  }

  def getResource [F[_]: Monad: Logger](path: String):  ReaderT[F, UserResourceLoader, Option[Resource]] = Kleisli { loader =>
    for {
      _ <- Logger[F].log (LOGCH, DEBUG, s"Attempting to get resource at path: $path")
      res <- path.endsWith ("/") match {
        case true => Monad[F].pure (None)
        case false => for {
          uo  <- Monad[F].pure { Try (Option (loader.getResource (path))).toOption.flatten }
          iso <- Monad[F].pure { Try (Option (loader.getResourceAsStream (path))).toOption.flatten }
          r <- (uo: Option[URL], iso: Option[InputStream]) match {
            case (Some (u), Some (is)) =>
              val fh = Resource (path, u, is)
              for {
              _ <- Logger[F].log(LOGCH, DEBUG, s"Successfully got resource, full resource url: $u")
              } yield Some (fh)
            case _ => for {
              _ <- Logger[F].log(LOGCH, DEBUG, s"Failed to get resource.")
            } yield None
          }
        } yield r: Option[Resource] // type definition needed here
      }
    } yield res
  }

  def findResourceForRequest[F[_]: Monad: Async: Logger] (request: HttpRequest): ReaderT[F, (UserResourceLoader, String, List[String]), Option[Resource]] = Kleisli { case (loader: UserResourceLoader, index: String, resourcePaths: List[String]) =>
    for {
      _ <- Logger[F].log (LOGCH, DEBUG, s"Attempting to find suitable resource for request to: ${request.path}")
      bestPathOpt <- resourcePaths match {
        case Nil => Monad[F].pure { None: Option[Resource] }
        case _ =>
          val possiblePaths = resourcePaths.map (p => p + request.path)
          val possibilities = request.path.contains('.') match {
            case true => possiblePaths
            case false => possiblePaths ::: possiblePaths.map (p => combinePath (p, index)) ::: Nil
          }
          for {
            _ <- Logger[F].log (LOGCH, DEBUG, s"Potential resource paths:\n* " + possibilities.mkString("\n* "))
            possibleFHsFL = possibilities.map (p => getResource[F] (p).run (loader)).sequence
            possibleFHsL <- possibleFHsFL
            pathOpt = possibleFHsL.find (_.isDefined).flatten
            _ <- pathOpt match {
              case Some (p) => Logger[F].log (LOGCH, INFO, s"Most suitable resource path found: " + p)
              case None => Logger[F].log (LOGCH, DEBUG, "No suitable resource path found.")
            }
          } yield pathOpt
      }
    } yield bestPathOpt
  }
}
