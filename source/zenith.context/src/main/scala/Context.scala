/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith.context

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

/**
 * WTF! For some reason Cats doesn't define `WriterT`
 */
final case class WriterT[T[_], L, V] (run: T[(L, V)]) {
  def written (implicit functorT: Functor[T]): T[L] = functorT.map (run)(_._1)
  def value (implicit functorT: Functor[T]): T[V] = functorT.map (run)(_._2)
  def map[Z](fn: V => Z)(implicit f: Functor[T]): WriterT[T, L, Z] = WriterT {
    f.map (run) { z =>
      (z._1, fn (z._2))
    }
  }
  def flatMap[U](f: V => WriterT[T, L, U])(implicit monadT: Monad[T], semigroupL: Semigroup[L]): WriterT[T, L, U] = WriterT {
    monadT.flatMap (run) { lv =>
      monadT.map (f (lv._2).run) { lv2 =>
        (semigroupL.combine (lv._1, lv2._1), lv2._2)
      }
    }
  }
}
object WriterT {
  def putT[T[_], L, V] (vt: T[V])(l: L)(implicit functorT: Functor[T]): WriterT[T, L, V] = WriterT (functorT.map (vt)(v => (l, v)))
  def put[T[_], L, V] (v: V)(l: L)(implicit functorT: Functor[T], applicativeT: Applicative[T]): WriterT[T, L, V] = WriterT.putT[T, L, V](applicativeT.pure (v))(l)
  def tell[T[_], L] (l: L)(implicit functorT: Functor[T], applicativeT: Applicative[T]): WriterT[T, L, Unit] = WriterT.put[T, L, Unit](())(l)
  def value[T[_], L, V] (v: V)(implicit functorT: Functor[T], applicativeT: Applicative[T], monoidL: Monoid[L]): WriterT[T, L, V] = WriterT.put[T, L, V](v)(monoidL.empty)
  def valueT[T[_], L, V] (vt: T[V])(implicit functorT: Functor[T], monoidL: Monoid[L]): WriterT[T, L, V] = WriterT.putT[T, L, V](vt)(monoidL.empty)

  implicit def writerTMonad[T[_], L] (implicit monadT: Monad[T], monoidL: Monoid[L]) = new Monad[({type WT[α] = WriterT[T, L, α]})#WT] {
    override def pure[A] (a: A): ({type WT[α] = WriterT[T, L, α]})#WT[A] = WriterT.value[T, L, A](a)
    override def flatMap[A, B] (fa: ({type WT[α] = WriterT[T, L, α]})#WT[A])(f: A => ({type WT[α] = WriterT[T, L, α]})#WT[B]): WriterT[T, L, B] = fa.flatMap (a => f (a))
    override def ap[A, B] (fa: ({type WT[α] = WriterT[T, L, α]})#WT[A])(ff: ({type WT[α] = WriterT[T, L, α]})#WT[A => B]): ({type WT[α] = WriterT[T, L, α]})#WT[B] = fa.flatMap (a => ff.map (f => f (a)))
  }
}

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


object Context {

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

  type WF[$] = WriterT[Future, LoggingContext, $]
  type EWT[$] = XorT[WF, Throwable, $]
  type CONTEXT[$] = EWT[$]

  def scalaFutureMonad (implicit ec: ExecutionContext): Monad[Future] = new Monad[Future] {
    override def pure[A] (a: A): Future[A] = Future (a)
    override def flatMap[A, B] (fa: Future[A])(f: A => Future[B]): Future[B] = fa.flatMap (a => f (a))
    override def ap[A, B] (fa: Future[A])(ff: Future[A => B]): Future[B] = fa.flatMap (a => ff.map (f => f (a)))
  }

  def context (ec: ExecutionContext): zenith.Context[CONTEXT] = new zenith.Context[CONTEXT] {
    implicit val monadFuture: Monad[Future] = scalaFutureMonad (ec)

    /** Logger */

    override def printAndClear[T](out: PrintStream, c: CONTEXT[T], onCrash: T): CONTEXT[T] = {
      import zenith.context.Extensions._
      val ctxT = c.toEither.written
      val vT = c.toEither.value.mapAll[T] {
        case Success (s) => s match {
          case Right (v) => out.println ("Task in context completed successfully."); v
          case Left (ex) =>
            import zenith.Extensions._
            out.println (s"Task in context completed with failure, exception found within context:")
            out.println (ex.getMessage)
            out.println (ex.stackTrace)
            onCrash
        }
        case Failure (f) =>
          import zenith.Extensions._
          out.println (s"Task in context completed with failed Future:")
          out.println (f.getMessage)
          out.println (f.stackTrace)
          onCrash
      }(ec)

      val f = ctxT.flatMap { ctx =>
        vT.map { v =>
          ctx.logs.foreach { x => out.printLog (x.channel, x.level, x.message) }
          out.println ()
          v
        }(ec)
      }(ec)

      // clear all exceptions and logs
      XorT.right[WF, Throwable, T](WriterT.valueT[Future, LoggingContext, T](f))
    }

    override def log (channel: => Option[String], level: => zenith.Logger.Level, message: => String): CONTEXT[Unit] = {
      Try { LoggingContext.log (channel, level, message) } match {
        case Success (s) => XorT.right[WF, Throwable, Unit](WriterT.put[Future, LoggingContext, Unit](())(s))
        case Failure (f) => XorT.left[WF, Throwable, Unit](WriterT.value[Future, LoggingContext, Throwable](f))
      }
    }

    /** Async */
    override def await[T](v: CONTEXT[T], seconds: Int): Either[Throwable, T] = {
      import scala.concurrent.duration._
      scala.concurrent.Await.result (v.toEither.value, seconds.seconds)
    }

    override def liftScalaFuture[T] (expression: => Future[T]): CONTEXT[T] = Try (expression) match {
      case Success (s) => XorT.right[WF, Throwable, T](WriterT.valueT[Future, LoggingContext, T](s))
      case Failure (f) => XorT.left[WF, Throwable, T] (WriterT.value[Future, LoggingContext, Throwable](f))
    }

    override def future[T] (expression: => T): CONTEXT[T] = Try (expression) match {
      case Success (s) => XorT.right[WF, Throwable, T](WriterT.value[Future, LoggingContext, T](s))
      case Failure (f) => XorT.left[WF, Throwable, T] (WriterT.value[Future, LoggingContext, Throwable](f))
    }

    override def success[T] (expression: => T): CONTEXT[T] = Try (expression) match {
      case Success (s) => XorT.right[WF, Throwable, T](WriterT.valueT[Future, LoggingContext, T](Future.successful (s)))
      case Failure (f) => XorT.left[WF, Throwable, T] (WriterT.value[Future, LoggingContext, Throwable](f))
    }

    override def failure[T] (expression: => Throwable): CONTEXT[T] = Try (expression) match {
      case Success (s) => XorT.left[WF, Throwable, T] (WriterT.value[Future, LoggingContext, Throwable](s))
      case Failure (f) => XorT.left[WF, Throwable, T] (WriterT.value[Future, LoggingContext, Throwable](f))
    }

    override def onComplete[T, X](v: CONTEXT[T], f: Try[T] => X): Unit = v.toEither.value.map { case Left (l) => throw l; case Right (r) => r }(ec).onComplete (f)(ec)
    override def promise[T] (): Async.Promise[CONTEXT, T] = new Async.Promise[CONTEXT, T] {
      private val p = Promise[T] ()
      def success (x: T): Unit = p.success (x)
      def failure (x: Throwable): Unit = p.failure (x)
      def future: CONTEXT[T] = XorT.right[WF, Throwable, T](WriterT.valueT[Future, LoggingContext, T](p.future))
    }

    /** Monad */
    override def pure[A] (a: A): CONTEXT[A] = future (a)
    override def flatMap[A, B] (fa: CONTEXT[A])(f: A => CONTEXT[B]): CONTEXT[B] = fa.flatMap (a => f (a))
    override def ap[A, B] (fa: CONTEXT[A])(ff: CONTEXT[A => B]): CONTEXT[B] = fa.flatMap (a => ff.map (f => f (a)))
  }
}
