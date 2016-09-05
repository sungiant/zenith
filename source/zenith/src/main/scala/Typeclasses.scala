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
import cats.Monad
import zenith.Extensions._
import java.io.PrintStream
import scala.collection.immutable.HashSet

/**
 * Context
 */
trait Context[Z[_]] extends Monad[Z] with Async[Z] with Logger[Z]
object Context {
  implicit def apply[Z[_]] (implicit m: Monad[Z], a: Async[Z], l: Logger[Z]): Context[Z] = {
    new Context[Z] with Monad[Z] with Async[Z] with Logger[Z] {
      /** Logger */
      override def printAndClear[T](out: PrintStream, v: Z[T], onCrash: T, verbosity: Logger.Level, channelFilter: HashSet[Logger.Channel]): Z[T] = l.printAndClear (out, v, onCrash, verbosity, channelFilter)
      override def log (channel: => Option[String], level: => zenith.Logger.Level, message: => String): Z[Unit] = l.log (channel, level, message)
      /** Async */
      override def await[T](v: Z[T], seconds: Int): Either[Throwable, T] = a.await (v, seconds)
      override def liftScalaFuture[T] (expression: => scala.concurrent.Future[T]): Z[T] = a.liftScalaFuture (expression)
      override def future[T] (expression: => T): Z[T] = a.future (expression)
      override def success[T] (expression: => T): Z[T] =  a.success (expression)
      override def failure[T] (expression: => Throwable): Z[T] = a.failure (expression)
      override def onComplete[T, X](v: Z[T], f: Try[T] => X): Unit = a.onComplete (v, f)
      override def promise[T] (): Async.Promise[Z, T] = a.promise ()
      /** Monad */
      override def pure[A] (a: A): Z[A] = m.pure (a)
      override def flatMap[A, B] (fa: Z[A])(f: A => Z[B]): Z[B] = m.flatMap(fa)(f)
      override def ap[A, B] (ff: Z[A => B])(fa: Z[A]): Z[B] = m.ap (ff)(fa)
    }
  }
}


/**
 * Logger
 */
@typeclass trait Logger[Z[_]] {
  import Logger._
  def printAndClear[T](out: PrintStream, v: Z[T], onCrash: T, verbosity: Logger.Level = Logger.Level.DEBUG, channelFilter: HashSet[Logger.Channel] = HashSet.empty): Z[T]
  def log (channel: => Option[String], level: => Level, message: => String): Z[Unit]

  /* HELPERS */
  def log (channel: => Option[String], level: => Level, throwable: Throwable): Z[Unit] = log (channel, level, throwable.stackTrace)
  def debug (message: => String): Z[Unit] = log (None, Level.DEBUG, message)
  def info (message: => String): Z[Unit] = log (None, Level.INFO, message)
  def warn (message: => String): Z[Unit] = log (None, Level.WARN, message)
  def error (message: => String): Z[Unit] = log (None, Level.ERROR, message)
  def trace (throwable: => Throwable): Z[Unit] = log (None, Level.ERROR, throwable)
}
object Logger {
  val ZENITH = Some ("ZENITH")
  type Channel = String
  case class Level (value: Int, name: String)
  object Level {
    val DEBUG = Level (1, "debug")
    val INFO = Level (2, "info")
    val WARN = Level (3, "warn")
    val ERROR = Level (4, "error")

    private val levels = List (DEBUG, INFO, WARN, ERROR)

    def apply(value: Int): Option[Level] = levels find (_.value == value)
    def apply(name: String): Option[Level] = levels find (_.name == name.toUpperCase)
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
  def onComplete[X, Y](v: Z[X], fn: Try[X] => Y): Unit

  /*
   * Async types can contain exceptions.
   * When we do a normal `map`, we only map over the success case.  This
   * function allows access to both the success or the failure case.
   */
  def transform[A, B] (v: Z[A])(fn: Try[A] => B): Z[B] = {
    val p = promise[B]()
    onComplete[A, Unit] (v, vResult =>
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
