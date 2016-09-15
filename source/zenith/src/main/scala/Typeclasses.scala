/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith

import simulacrum._
import scala.util.{Try, Success, Failure}
import zenith.Extensions._
import java.io.PrintStream
import scala.collection.immutable.HashSet
import cats.std.all._
import cats.syntax.eq._

/**
 * Logger
 */
@typeclass trait Logger[Z[_]] { import Logger._

  // An implementation of this function should print to the output stream any
  // logs found within the context and also clears those logs from the context.
  // If unwinding the context to access the logs fails for whatever reason it
  // the `onCrash` exception should be use appropriately for the given implementation.
  def extract[T](v: Z[T])(out: PrintStream, onCrash: T): Z[T]

  // An implementation of this function should adds a log to the context.
  def log (channel: => Option[Channel], level: => Level, message: => String): Z[Unit]

  /* HELPERS */
  def debug (message:   => String   )(implicit channel: Option[Channel] = None): Z[Unit] = log (channel, Level.DEBUG, message)
  def info  (message:   => String   )(implicit channel: Option[Channel] = None): Z[Unit] = log (channel, Level.INFO,  message)
  def warn  (message:   => String   )(implicit channel: Option[Channel] = None): Z[Unit] = log (channel, Level.WARN,  message)
  def error (message:   => String   )(implicit channel: Option[Channel] = None): Z[Unit] = log (channel, Level.ERROR, message)
  def trace (throwable: => Throwable)(implicit channel: Option[Channel] = None): Z[Unit] = log (channel, Level.ERROR, throwable.stackTrace)
}
object Logger {
  type Channel = String
  case class Level (value: Int, name: String)
  object Level {
    val DEBUG = Level (1, "debug")
    val INFO  = Level (2, "info")
    val WARN  = Level (3, "warn")
    val ERROR = Level (4, "error")

    private val levels = List (DEBUG, INFO, WARN, ERROR)

    def apply (value: Int): Option[Level] = levels.find (_.value === value)
    def apply (name: String): Option[Level] = levels.find (_.name.toLowerCase === name.toLowerCase)
  }
}


/**
 * Async
 *
 * This typeclass is an abstraction over common features available in different flavours of `Future`, like:
 * - scala.concurrent.Future
 * - com.twitter.util.Future
 * - scala.actor.Future
 * - java.util.concurrent.Future
 *
 */
@typeclass trait Async[Z[_]] {
  def await[T](v: Z[T], seconds: Int): Either[Throwable, T]
  def promise[T](): Async.Promise[Z, T]
  def future[T] (expression: => T): Z[T]
  def success[T] (expression: => T): Z[T]
  def failure[T] (expression: => Throwable): Z[T]

  def liftScalaFuture[T] (fx: => scala.concurrent.Future[T]): Z[T]

  /*
   * When this future is completed, either through an exception, or a value,
   *  apply the provided function.
   *
   *  If the future has already been completed, this will either be applied immediately or be scheduled asynchronously.
   */
  def onComplete[X, Y](v: Z[X])(fn: Try[X] => Y): Unit

  /*
   * Async types can contain exceptions.
   * When we do a normal `map`, we only map over the success case.  This
   * function allows access to both the success or the failure case.
   */
  def transform[A, B] (v: Z[A])(fn: Try[A] => B): Z[B] = {
    val p = promise[B]()
    onComplete[A, Unit] (v)(vResult =>
      // Need a try here because `fn` may throw exceptions itself.
      try {
        val result = fn (vResult)
        p.success (result)
      } catch { case t: Throwable => p.failure (t) })

    p.future
  }
}
object Async {
  trait Promise[Z[_], T] {
    def success (x: T): Unit
    def failure (x: Throwable): Unit
    def future: Z[T]
  }
}
