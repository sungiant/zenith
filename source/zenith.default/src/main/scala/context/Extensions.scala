/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith.default.context

import cats._
import cats.data._
import cats.implicits._

import scala.util.{Try, Success, Failure}
import scala.concurrent.{Promise, Future, ExecutionContext}
import java.io.PrintStream

import zenith._
import scala.collection.immutable.HashSet

private [this] object Extensions extends PrintStreamExtensions with FutureExtensions

/********************************************************************************************************************/

private [this] trait FutureExtensions {
  implicit class Implicit[T](val thisF: Future[T]) {
    def mapAll[Target] (f: Try[T] => Target)(implicit ec: ExecutionContext): Future[Target] = {
      val promise = Promise[Target]()
      thisF.onComplete {
        thisR => try {
          val result = f (thisR)
          promise.success (result)
        } catch {
          case t: Throwable => promise.failure (t)
        }
      }(ec)
      promise.future
    }
  }
}

/********************************************************************************************************************/

private [this] trait PrintStreamExtensions {
  implicit class Implicit (val out: PrintStream) {
    
    import java.util.Locale
    import scala.io.AnsiColor

    private lazy val isANSISupported = {
      Option (System.getProperty ("sbt.log.noformat")).map (_ != "true").orElse {
        Option (System.getProperty ("os.name"))
          .map (_.toLowerCase (Locale.ENGLISH))
          .filter (_.contains ("windows"))
          .map (_ => false)
      }.getOrElse (true)
    }

    private def colouredName (level: zenith.Logger.Level) = {
      val colours = Map ("debug" -> AnsiColor.CYAN, "info" -> AnsiColor.YELLOW, "warn" -> AnsiColor.YELLOW, "error" -> AnsiColor.RED)
      colours.getOrElse (level.name, AnsiColor.BLACK) + level.name + AnsiColor.RESET
    }

    // Prints a single log to the print stream.
    def printLog (channel: Option[String], level: zenith.Logger.Level, message: String)(colourLogsIfPossible: Boolean = true): Unit = {
      def ident (withAnsiColours: Boolean) = withAnsiColours match {
        case true =>
          channel match {
            case Some (c) => "[" + AnsiColor.WHITE + c + AnsiColor.RESET + "][" + colouredName (level) + "] "
            case None => "[" + colouredName (level) + "] "
          }
        case false =>
          channel match {
            case Some (c) => "[" + c + "][" + level.name + "] "
            case None => "[" + level.name + "] "
          }
      }

      val printPrettyLogs = isANSISupported && colourLogsIfPossible

      // TODO: Add proper support for filtering logs by channel and debug.  Right now this is just hard coded to hide all
      (channel, level) match {
        //case (zenith.Logger.LOGCH, zenith.Logger.Level.DEBUG) => ()
        case _ =>
          message.split ('\n').toList match {
            case head :: tail =>
              val padding = " " * ident (withAnsiColours = false).size
              out.println (ident (printPrettyLogs) + head)
              tail.foreach (m => out.println (padding + m))
            case _ =>
              out.println (ident (printPrettyLogs) + message)
          }
      }
    }
  }
}
