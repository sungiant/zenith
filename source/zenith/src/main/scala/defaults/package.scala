/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith

import cats.Monad.ops._
import cats.Functor.ops._
import cats._
import cats.data._

import scala.util.{Try, Success, Failure}
import scala.concurrent.{Promise, Future, ExecutionContext}
import java.io.PrintStream

import zenith.defaults.Extensions._

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
package object defaults {

  private type WF[$] = WriterT[Future, LoggingContext, $]
  type CONTEXT[$] = XorT[WriterT[Future, LoggingContext, ?], Throwable, $]

  def fpContext (ec: ExecutionContext): zenith.Context[CONTEXT] = new zenith.Context[CONTEXT] {

    implicit val monadFuture: Monad[Future] = new Monad[Future] {
      override def pure[A] (a: A): Future[A] = Future (a)(ec)
      override def flatMap[A, B] (fa: Future[A])(f: A => Future[B]): Future[B] = fa.flatMap (a => f (a))(ec)
      override def ap[A, B] (ff: Future[A => B])(fa: Future[A]): Future[B] = fa.flatMap (a => ff.map (f => f (a))(ec))(ec)
    }

    /** Logger */
    override def printAndClear[T](out: PrintStream, c: CONTEXT[T], onCrash: T): CONTEXT[T] = {
      // TODO: This could be incorrectly triggering twice on some exceptions; write tests to investigate.
      val ctxT = c.toEither.written
      val vT = c.toEither.value.mapAll[T] {
        case Success (s) => s match {
          case Right (v) => out.println ("Task in context completed successfully."); v
          case Left (ex) =>
            import zenith.Extensions._
            out.println (s"Task in context completed with failure, exception found within context:")
            out.println (s"Failed context message: ${ex.getMessage}")
            out.println (s"Failed context stack trace:")
            out.println (ex.stackTrace)
            onCrash
        }
        case Failure (f) =>
          import zenith.Extensions._
          out.println (s"Task in context completed with failed Future.")
          out.println (s"Failed Future message: ${f.getCause.getMessage}")
          out.println (s"Failed Future stack trace:")
          out.println (f.getCause.stackTrace)

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
        case Success (s) =>
            //println (s)
            XorT.right[WF, Throwable, Unit](WriterT.put[Future, LoggingContext, Unit](())(s))
        case Failure (f) =>
            //println (f)
            XorT.left[WF, Throwable, Unit](WriterT.value[Future, LoggingContext, Throwable](f))
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
    override def ap[A, B] (ff: CONTEXT[A => B])(fa: CONTEXT[A]): CONTEXT[B] = fa.flatMap (a => ff.map (f => f (a)))
  }
}
