package demo

import zenith.{Async, Context, HttpResponse}
import scala.concurrent.{Promise => ScalaPromise, Future => ScalaFuture, ExecutionContext}
import scala.util.Try
import cats.Monad.ops._
import cats._
import cats.state._
import cats.data._

object ImperativeContext {

  type CONTEXT[$] = ScalaFuture[$]

  def context (ec: ExecutionContext): Context[CONTEXT] = new Context[CONTEXT] {
    private implicit val * = ec

    /** Logger */
    override def debug (msg: String): CONTEXT[Unit] = success (println (s"DEBUG $msg"))
    override def info (msg: String): CONTEXT[Unit] = success (println (s"INFO $msg"))
    override def warn (msg: String): CONTEXT[Unit] = success (println (s"WARN $msg"))
    override def error (msg: String): CONTEXT[Unit] = success (println (s"ERROR $msg"))

    /** Async */
    override def future[T] (x: T): CONTEXT[T] = ScalaFuture (x)
    override def success[T] (x: T): CONTEXT[T] = ScalaFuture.successful (x)
    override def failure[T] (x: Throwable): CONTEXT[T] = ScalaFuture.failed (x)
    override def onComplete[T, X](v: CONTEXT[T], f: Try[T] => X): Unit = v.onComplete (f)
    override def promise[T] (): Async.Promise[CONTEXT, T] = new Async.Promise[ScalaFuture, T] {
      private val p = ScalaPromise[T] ()
      def success (x: T): Unit = p.success (x)
      def failure (x: Throwable): Unit = p.failure (x)
      def future: CONTEXT[T] = p.future
    }

    /** Monad */
    override def pure[A](a: A): CONTEXT[A] = future (a)
    override def flatMap[A, B](fa: CONTEXT[A])(f: A => CONTEXT[B]): CONTEXT[B] = fa.flatMap (f)
    override def ap[A, B](fa: CONTEXT[A])(ff: CONTEXT[A => B]): CONTEXT[B] = for { a <- fa; f <- ff } yield f (a)
  }
}