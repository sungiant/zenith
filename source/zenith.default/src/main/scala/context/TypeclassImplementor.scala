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

import scala.annotation.tailrec
import scala.util.{Try, Success, Failure}
import scala.concurrent.{Promise, Future, ExecutionContext}
import java.io.PrintStream

import zenith._
import scala.collection.immutable.HashSet

private [this] object TypeclassImplementor {

  type WF[$] = WriterT[Future, LoggingContext, $]

  // This function creates a implementation of the Zenith's `Zen` typeclass for the default
  // context type `Context`
  def createBundledTypeclassImplementationsForType (
    // CONFIG
    colourLogs: Boolean,
    defaultVebosity: Logger.Level,
    verbosityOverrides: Map[Logger.Channel, Logger.Level])(implicit 
    ec: ExecutionContext) = new Monad[Type] with Async[Type] with Logger[Type] {

    implicit val monadFuture: Monad[Future] = new Monad[Future] {
      override def pure[A] (a: A): Future[A] = Future (a)(ec)
      override def flatMap[A, B] (fa: Future[A])(f: A => Future[B]): Future[B] = fa.flatMap (a => f (a))(ec)
      override def ap[A, B] (ff: Future[A => B])(fa: Future[A]): Future[B] = fa.flatMap (a => ff.map (f => f (a))(ec))(ec)
      override def tailRecM[A, B](a: A)(f: A => Future[Either[A, B]]): Future[B] = defaultTailRecM (a)(f)
    }

    /** implement Monad ***********************************************************************************************/
    override def pure[A] (a: A): Type[A] = future (a)
    override def flatMap[A, B](fa: Type[A])(f: A => Type[B]): Type[B] = fa.flatMap (a => f (a))
    override def ap[A, B](ff: Type[A => B])(fa: Type[A]): Type[B] = fa.flatMap (a => ff.map (f => f (a)))
    override def tailRecM[A, B](a: A)(f: A => Type[Either[A, B]]): Type[B] = defaultTailRecM (a)(f)

    /** implement Async ***********************************************************************************************/
    override def await[T](v: Type[T], seconds: Int): Either[Throwable, T] = {
      import scala.concurrent.duration._
      scala.concurrent.Await.result (v.toEither.value, seconds.seconds)
    }

    override def liftScalaFuture[T] (expression: => Future[T]): Type[T] = Try (expression) match {
      case Success (s) => XorT.right[WF, Throwable, T](WriterT.valueT[Future, LoggingContext, T](s))
      case Failure (f) => XorT.left[WF, Throwable, T] (WriterT.value[Future, LoggingContext, Throwable](f))
    }

    override def future[T] (expression: => T): Type[T] = Try (expression) match {
      case Success (s) => XorT.right[WF, Throwable, T](WriterT.value[Future, LoggingContext, T](s))
      case Failure (f) => XorT.left[WF, Throwable, T] (WriterT.value[Future, LoggingContext, Throwable](f))
    }

    override def success[T] (expression: => T): Type[T] = Try (expression) match {
      case Success (s) => XorT.right[WF, Throwable, T](WriterT.valueT[Future, LoggingContext, T](Future.successful (s)))
      case Failure (f) => XorT.left[WF, Throwable, T] (WriterT.value[Future, LoggingContext, Throwable](f))
    }

    override def failure[T] (expression: => Throwable): Type[T] = Try (expression) match {
      case Success (s) => XorT.left[WF, Throwable, T] (WriterT.value[Future, LoggingContext, Throwable](s))
      case Failure (f) => XorT.left[WF, Throwable, T] (WriterT.value[Future, LoggingContext, Throwable](f))
    }

    override def onComplete[T, X](v: Type[T])(f: Try[T] => X): Unit = v.toEither.value.map { case Left (l) => throw l; case Right (r) => r }(ec).onComplete (f)(ec)
    override def promise[T] (): Async.Promise[Type, T] = new Async.Promise[Type, T] {
      private val p = Promise[T] ()
      def success (x: T): Unit = p.success (x)
      def failure (x: Throwable): Unit = p.failure (x)
      def future: Type[T] = XorT.right[WF, Throwable, T](WriterT.valueT[Future, LoggingContext, T](p.future))
    }

    /** implement Logger **********************************************************************************************/
    override def extract[T](c: Type[T])(out: PrintStream, onCrash: T): Type[T] = { //(Type[T], List[(Option[Logger.Channel], Logger.Level, String)]) = {
      import Extensions._
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
          val filtered: List[Log] = ctx.logs.filter { log =>
            val verbosityForChannel = log.channel.flatMap (verbosityOverrides.get).getOrElse(defaultVebosity)
            verbosityForChannel.value <= log.level.value
          }

          // perhaps this FN should be pure too and return a tuple with this filtered list as the additional 2nd value

          filtered.foreach (x => out.printLog (x.channel, x.level, x.message)(colourLogs))
          out.println ()
          v
          //filtered.map { log =>
          //  (log.channel, log.level, log.message)
          //}
        }(ec)
      }(ec)

      // clear all exceptions and logs
      XorT.right[WF, Throwable, T](WriterT.valueT[Future, LoggingContext, T](f))
    }

    override def log (channel: => Option[String], level: => zenith.Logger.Level, message: => String): Type[Unit] = {
      Try { LoggingContext.log (channel, level, message) } match {
        case Success (s) => XorT.right[WF, Throwable, Unit](WriterT.put[Future, LoggingContext, Unit](())(s))
        case Failure (f) => XorT.left[WF, Throwable, Unit](WriterT.value[Future, LoggingContext, Throwable](f))
      }
    }
  }
}
