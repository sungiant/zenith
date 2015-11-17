/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith.defaults

import zenith.{Async, HttpResponse}
import cats.Monad.ops._
import cats.Functor.ops._
import cats._
import cats.state._
import cats.data._

import scala.util.{Try, Success, Failure}
import scala.concurrent.{Promise, Future, ExecutionContext}
import java.io.PrintStream

case class Log (channel: Option[String], level: zenith.Logger.Level, message: String)

case class LoggingContext (logs: List[Log])
object LoggingContext {
implicit val monoidLoggingContext = new Monoid[LoggingContext] {
  override val empty = LoggingContext (Nil)
  override def combine (f1: LoggingContext, f2: LoggingContext) = LoggingContext (f1.logs ::: f2.logs)
}
def log (channel: Option[String], level: zenith.Logger.Level, message: String) =
  LoggingContext (Log (channel, level, message) :: Nil)
}