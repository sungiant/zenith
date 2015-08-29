package demo

import zenith.{Async, Context, HttpResponse}
import scala.concurrent.{Promise => ScalaPromise, Future => ScalaFuture, ExecutionContext}
import scala.util.Try
import cats.Monad.ops._
import cats.Functor.ops._
import cats._
import cats.state._
import cats.data._

object FunctionalContext {
  import FunctionalContext.{LoggingContext => LC}

  type WF[$] = WriterT[ScalaFuture, LC, $]
  type EWT[$] = EitherT[WF, Throwable, $]

  type CONTEXT[$] = EWT[$]

  sealed trait LogLevel
  case object LogLevelError extends LogLevel { override def toString = "ERROR" }
  case object LogLevelWarning extends LogLevel { override def toString = " WARN" }
  case object LogLevelInfo extends LogLevel { override def toString = " INFO" }
  case object LogLevelDebug extends LogLevel { override def toString = "DEBUG" }

  case class Log (level: LogLevel, message: String)
  case class LoggingContext (logs: List[Log] = Nil)

  object LoggingContext {
    implicit val monoid = new Monoid[LoggingContext] {
      override val empty = LoggingContext ()
      override def combine (f1: LoggingContext, f2: LoggingContext) = LoggingContext (f1.logs ::: f2.logs)
    }

    def print (w: LoggingContext): Unit = {
      w.logs.foreach { x =>
          x.message.split('\n').toList match {
            case head :: tail =>
              println ("$ " + s"${x.level}: $head")
              val pad = " " * x.level.toString.size
              tail.foreach (m => println ("  " + s"$pad: $m"))
            case _ =>
              println ("$ " + s"${x.level}: ${x.message}")
        }
      }
      println ("")
    }

    def error (message: String) = LoggingContext (Log (LogLevelError, message) :: Nil)
    def warn (message: String) = LoggingContext (Log (LogLevelWarning, message) :: Nil)
    def info (message: String) = LoggingContext (Log (LogLevelInfo, message) :: Nil)
    def debug (message: String) = LoggingContext (Log (LogLevelDebug, message) :: Nil)
  }

  def scalaFutureMonad (implicit ec: ExecutionContext) = new Monad[ScalaFuture] {
    override def pure[A] (a: A): ScalaFuture[A] = ScalaFuture (a)
    override def flatMap[A, B] (fa: ScalaFuture[A])(f: A => ScalaFuture[B]): ScalaFuture[B] = fa.flatMap (a => f (a))
    override def ap[A, B] (fa: ScalaFuture[A])(ff: ScalaFuture[A => B]): ScalaFuture[B] = fa.flatMap (a => ff.map (f => f (a)))
  }


  // todo, not sure how to magical define this, but i know if can be done
  def WFMonad (implicit m: Monad[ScalaFuture]): Monad[WF] = new Monad[WF] {
    override def pure[A] (a: A): WF[A] = WriterT.value[ScalaFuture, LC, A](a)
    override def flatMap[A, B] (fa: WF[A])(f: A => WF[B]): WF[B] = fa.flatMap (a => f (a))
    override def ap[A, B] (fa: WF[A])(ff: WF[A => B]): WF[B] = fa.flatMap (a => ff.map (f => f (a)))
  }


  def handle[T] (ec: ExecutionContext, onCrash: T)(c: CONTEXT[T]): CONTEXT[T] = {
    implicit val e = ec
    implicit val fm = scalaFutureMonad (ec)
    implicit val monadWF = WFMonad (fm)

    val ctxT = c.run.written
    val vT = c.run.value.map {
      case Right (v) =>
        println ("Great success!")
        v
      case Left (ex) =>
        println (s"OH SNAP! Embedded exception found in context: $ex")
        onCrash
    }
    val f = for {
      ctx <- ctxT
      v <- vT
    } yield {
      println ("(context logs attached)")
      LoggingContext.print (ctx)
      v
    }

    // clear all exceptions and logs
    EitherT.right[WF, Throwable, T](WriterT.valueT[ScalaFuture, LC, T](f))
  }

  def context (ec: ExecutionContext): Context[CONTEXT] = new Context[CONTEXT] {
    private implicit val fm = scalaFutureMonad (ec)
    private implicit val monadWF = WFMonad (fm)

    /** Logger */
    override def debug (msg: String): CONTEXT[Unit] = EitherT.right[WF, Throwable, Unit](WriterT.put[ScalaFuture, LC, Unit](())(LC.debug (msg)))
    override def info (msg: String): CONTEXT[Unit] = EitherT.right[WF, Throwable, Unit](WriterT.put[ScalaFuture, LC, Unit](())(LC.info (msg)))
    override def warn (msg: String): CONTEXT[Unit] = EitherT.right[WF, Throwable, Unit](WriterT.put[ScalaFuture, LC, Unit](())(LC.warn (msg)))
    override def error (msg: String): CONTEXT[Unit] = EitherT.right[WF, Throwable, Unit](WriterT.put[ScalaFuture, LC, Unit](())(LC.error (msg)))

    /** Async */
    override def future[T] (x: T): CONTEXT[T] = EitherT.right[WF, Throwable, T](WriterT.value[ScalaFuture, LC, T](x))
    override def success[T] (x: T): CONTEXT[T] = EitherT.right[WF, Throwable, T](WriterT.valueT[ScalaFuture, LC, T](ScalaFuture.successful (x)))
    override def failure[T] (x: Throwable): CONTEXT[T] = EitherT.left[WF, Throwable, T] (WriterT.value[ScalaFuture, LC, Throwable](x))
    override def onComplete[T, X](v: CONTEXT[T], f: Try[T] => X): Unit = v.run.value.map { case Left (l) => throw l; case Right (r) => r }(ec).onComplete (f)(ec)
    override def promise[T] (): Async.Promise[CONTEXT, T] = new Async.Promise[CONTEXT, T] {
      private val p = ScalaPromise[T] ()
      def success (x: T): Unit = p.success (x)
      def failure (x: Throwable): Unit = p.failure (x)
      def future: CONTEXT[T] = EitherT.right[WF, Throwable, T](WriterT.valueT[ScalaFuture, LC, T](p.future))
    }

    /** Monad */
    override def pure[A] (a: A): CONTEXT[A] = future (a)
    override def flatMap[A, B] (fa: CONTEXT[A])(f: A => CONTEXT[B]): CONTEXT[B] = fa.flatMap (a => f (a))
    override def ap[A, B] (fa: CONTEXT[A])(ff: CONTEXT[A => B]): CONTEXT[B] = fa.flatMap (a => ff.map (f => f (a)))
  }
}