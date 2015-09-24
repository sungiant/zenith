/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith.context.test

import zenith._
import org.specs2.mutable._
import zenith.context.Extensions._
import zenith.Extensions._
import cats._
import cats.std.all._

import scala.concurrent.Future

import zenith.context.Context.{Log, LoggingContext}
import zenith.Logger.Level._

class ContextSpec extends Specification {
  type Z[$] = zenith.context.Context.CONTEXT[$]
  val ec = scala.concurrent.ExecutionContext.Implicits.global
  implicit val monadFuture = zenith.context.Context.scalaFutureMonad (ec)
  implicit val context = zenith.context.Context.context (ec)

  "Using the default Context implementation" should {
    "correctly collect logs" in {
      val c: Z[HttpResponse] = for {
          _ <- Logger[Z].debug ("Log #1")
          res <- Async[Z].success { HttpResponse.plain (200) }
          _ <- Logger[Z].debug ("Log #2")
        } yield res

      c.toEither.written.map (_ must_== LoggingContext (Log (None, DEBUG, "Log #1") :: Log (None, DEBUG, "Log #2") :: Nil)).await
    }
    "correctly collect logs when an exception in thrown in an `Async[Z].success` expression" in {
      def failResponse: HttpResponse = throw new Exception ("Yuck")
      val c: Z[HttpResponse] = for {
        _ <- Logger[Z].debug ("Log #1")
        res <- Async[Z].success { failResponse }
        _ <- Logger[Z].debug ("Log #2")
      } yield res

      c.toEither.written.map { x =>
        x must_== LoggingContext (Log (None, DEBUG, "Log #1") :: Nil)
      }.await
    }
  }
}