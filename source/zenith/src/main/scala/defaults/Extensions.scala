/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith.defaults

/**
 * This package provides a functional implementation of a `Zenith Context`.
 *
 * This package is not required to use Zenith, rather it is simply an example of how one may choose
 * to implement a `Zenith Context`.
 *
 * The implementation uses Scala `Future` to provide the context's asynchronous functionality and a stack of
 * Monad Transformers to encapsulate all logging and errors.  This implementation could easily be changed to use a
 * Twitter or Akka `Future` in place of the Scala `Future`.
 */

import zenith.{Async, HttpResponse}
import cats.Monad.ops._
import cats.Functor.ops._
import cats._
import cats.state._
import cats.data._

import scala.util.{Try, Success, Failure}
import scala.concurrent.{Promise, Future, ExecutionContext}
import java.io.PrintStream

object Extensions extends PrintStreamExtensions with FutureExtensions

trait FutureExtensions {
  implicit class Implicit[T](val thisF: Future[T]) {
    def mapAll[Target] (f: Try[T] => Target)(implicit ec: ExecutionContext): Future[Target] = {
      val promise = Promise[Target]()
      thisF.onComplete {
        thisR => try {
          val result = f (thisR)
          promise success result
        } catch {
          case t: Throwable => promise failure t
        }
      }(ec)
      promise.future
    }
  }
}

trait PrintStreamExtensions {
  implicit class Implicit (val out: PrintStream) {

    import scala.io.AnsiColor, java.util.Locale
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

    def printLog (channel: Option[String], level: zenith.Logger.Level, message: String): Unit = {
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

      // TODO: Add proper support for filtering logs by channel and debug.  Right now this is just hard coded to hide all
      (channel, level) match {
        //case (zenith.Logger.ZENITH, zenith.Logger.Level.DEBUG) => ()
        case _ =>
          message.split ('\n').toList match {
            case head :: tail =>
              val padding = " " * ident (withAnsiColours = false).size
              out.println (ident (isANSISupported) + head)
              tail.foreach (m => out.println (padding + m))
            case _ =>
              out.println (ident (isANSISupported) + message)
          }
      }
    }
  }
}
