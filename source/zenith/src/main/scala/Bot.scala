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
import cats.state._
import cats.data._
import cats.std.all._
import cats.Traverse.ops._
import cats.Monad.ops._
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
final case class Assertion[Z[_]: Context, ClientState, TRequest, TResponse] (
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
abstract class Bot[Z[_]: Context, ClientState](implicit m: Monad[Z]) {
  def createStartState (): ClientState
  val actions: List[Action[Z, ClientState]]

  final def run (httpClient: HttpClient[Z], contextHandler: Z[(ClientState, Result)] => Z[(ClientState, Result)]): Z[Result] = {
    type V[I] = StateT[Z, ClientState, I]
    actions
      .map (_.run (httpClient, contextHandler))
      .sequence[V, Result]
      .map (Foldable[List].fold (_))
      .runA (createStartState ())
  }
}


/**
 * Action
 */
abstract class Action[Z[_]: Context, ClientState] {
  def run (httpClient: HttpClient[Z], contextHandler: Z[(ClientState, Result)] => Z[(ClientState, Result)]): StateT[Z, ClientState, Result]
}
abstract class ActionT[Z[_]: Context, ClientState, TRequest, TResponse] extends Action [Z, ClientState] {
  def requestMapper (r: TRequest): HttpRequest
  def responseMapper (r: HttpResponse): Option[TResponse]
  def request: ReaderT[Z, ClientState, TRequest]

  def before: Option[ClientState => ClientState] = None
  def after: Option[(ClientState, TResponse) => ClientState] = None
  def lastly: Option[ClientState => ClientState] = None

  def assertions: List[Assertion[Z, ClientState, TRequest, TResponse]] = {
    import java.lang.reflect.{Method => ReflectedMethod}
    def isAssertion (fn: ReflectedMethod): Boolean = Option { fn.getAnnotation (classOf[assertion]) }.isDefined
    super
      .getClass
      .getMethods
      .collect { case m if isAssertion (m) => m }
      .map (Assertion (this, _))
      .toList
  }

  final val id = super.getClass.getSimpleName.splitCamelCase
  final val description: String = super
    .getClass
    .getAnnotations
    .filter (x => x.annotationType == classOf[description])
    .map (x => x.asInstanceOf[description].value)
    .headOption
    .getOrElse ("?")

  final def run (httpClient: HttpClient[Z], contextHandler: Z[(ClientState, Result)] => Z[(ClientState, Result)]): StateT[Z, ClientState, Result] = ActionT.run (this, httpClient, contextHandler)
}
object ActionT {
  private def liftStateT[Z[_], S, V] (v: Z[V])(implicit m: Monad[Z]): StateT[Z, S, V] =
    StateT[Z, S, V] { s => v.map ((s, _)) }

  private def liftStateT[Z[_], X, V] (r: ReaderT[Z, X, V])(implicit m: Monad[Z]): StateT[Z, X, V] = {
    type $[%, &] = StateT[Z, %, &]
    val ms = MonadState[$, X]
    for {
      s <- ms.get
      r <- liftStateT[Z, X, V] (r.run (s))
    } yield r
  }

  def run[Z[_]: Context, ClientState, TRequest, TResponse] (
    action: ActionT[Z, ClientState, TRequest, TResponse], httpClient: HttpClient[Z], contextHandler: Z[(ClientState, Result)] => Z[(ClientState, Result)]): StateT[Z, ClientState, Result] = {
    type U[I, J] = StateT[Z, I, J]
    type V[I] = StateT[Z, ClientState, I]
    val ms = MonadState[U, ClientState]
    val async = Async[Z]
    val logger = Logger[Z]
    def debug (s: String) = liftStateT[Z, ClientState, Unit] (logger.debug (s))
    def info (s: String) = liftStateT[Z, ClientState, Unit] (logger.info (s))

    def runAssertions (action: ActionT[Z, ClientState, TRequest, TResponse], response: TResponse): StateT[Z, ClientState, Result] = {
      val assertions = action.assertions.map { assertion => for {
          r <- liftStateT[Z, ClientState, Result] (assertion.run(response))
          _ <- info (s"[$r] ${assertion.description}")
        } yield r
      }
      for {
        cs <- ms.get
        r <- assertions.sequence[V, Result].map (Foldable[List].fold (_))
        newState = action.after.map (f => f (cs, response)).getOrElse (cs)
        _ <- ms.set (newState)
      } yield r
    }

    val task: StateT[Z, ClientState, Result] = for {
      _ <- info (s"${action.description}")

      // pull out the initial state
      startState <- ms.get

      // apply the `before` task if it is defined
      beforeState = action.before.map (f => f (startState)).getOrElse (startState)

      // write through the new state
      _ <- ms.set (beforeState)

      // build the request
      typedRequest <- liftStateT (action.request)

      // map it to a plain http request
      httpRequest = action.requestMapper (typedRequest)

      _ <- debug (httpRequest.toPrettyString)

      // send the http request
      httpExchange <- liftStateT (httpClient.sendEx (httpRequest))

      // handle failed http requests
      _ <- httpExchange.result match {
        case Left (t) => liftStateT[Z, ClientState, Unit] (async.failure[Unit] (t))
        case Right (httpResponse) => debug (httpResponse.toPrettyString)
      }

      // run the assertions associated with the action
      result <- httpExchange.result match {
        case Left (error) => StateT.pure[Z, ClientState, Result] (Failed)
        case Right (httpResponse) => action.responseMapper (httpResponse) match {
          case None => StateT.pure[Z, ClientState, Result] (Failed)
          case Some (response) => runAssertions (action, response)
        }
      }

      // get the state after having run all of the assertions
      afterState <- ms.get

      // apply the `lastly` task if it is defined
      lastlyState = action.lastly.map (f => f (afterState)).getOrElse (afterState)

      // write the final state
      _ <- ms.set (afterState)
    } yield result

    // Apply context handler.
    StateT[Z, ClientState, Result] { cs => contextHandler (task.run (cs)) }
  }
}

