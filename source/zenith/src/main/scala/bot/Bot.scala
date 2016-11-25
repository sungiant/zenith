/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith.bot

import zenith._
import zenith.client._
import cats._
import cats.data._
import cats.implicits._
import zenith.Extensions._
import scala.util.Try
import java.lang.reflect.{Method => ReflectedMethod}

/**
 * Result
 */
sealed trait Result
object Result {
  implicit val m: Monoid[Result] = new Monoid[Result] {
    val empty: Result = Passed
    def combine (x: Result, y: Result): Result = (x, y) match {
      case (Passed, Passed) => Passed
      case _ => Failed
    }
  }
}
object Passed extends Result { override def toString = "PASS" }
object Failed extends Result { override def toString = "FAIL" }


/**
 * Assertion
 */
final case class Assertion[Z[_]: Monad: Async: Logger, ClientState, TRequest, TResponse](
   parent: ActionT[Z, ClientState, TRequest, TResponse], fn: ReflectedMethod) {
  val id = fn.getName.splitCamelCase
  val annotations = fn.getAnnotations
  val description = annotations
    .filter (x => x.annotationType == classOf[description])
    .map (x => x.asInstanceOf[description].value)
    .headOption
    .getOrElse ("Description Missing!")

  def run (response: TResponse) = fn.invoke (parent, response.asInstanceOf[Object]).asInstanceOf[ReaderT[Z, ClientState, Result]]
}

/**
 * Bot
 */
abstract class Bot[Z[_]: Monad: Async: Logger, ClientState] {
  def createStartState (): ClientState
  val actions: List[Action[Z, ClientState]]

  final def run (httpClient: HttpClient[Z]): Z[Result] = {
    type V[I] = StateT[Z, ClientState, I]
    actions
      .map (_.run (httpClient, () => createStartState ()))
      .sequence[V, Result]
      .map (Foldable[List].fold (_))
      .runA (createStartState ())
  }
}
