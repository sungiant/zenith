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
import scala.util.{Try, Success, Failure}
import java.lang.reflect.{Method => ReflectedMethod}

/**
 * Action
 */
abstract class Action[Z[_], ClientState] {
  def run (httpClient: HttpClient[Z], createStartState: () => ClientState): StateT[Z, ClientState, Result]
}
abstract class ActionT[Z[_]: Monad: Async: Logger, ClientState, TRequest, TResponse] extends Action [Z, ClientState] {
  def requestMapper: ReaderT[Z, TRequest, HttpRequest]
  def responseMapper: ReaderT[Z, HttpResponse, Try[TResponse]]
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

  final def run (httpClient: HttpClient[Z], createStartState: () => ClientState): StateT[Z, ClientState, Result] = ActionT.run (this, httpClient, createStartState)
}
object ActionT {
  private def liftStateT[Z[_]: Monad, S, V] (v: Z[V]): StateT[Z, S, V] =
    StateT[Z, S, V] { s => v.map ((s, _)) }

  private def liftStateT[Z[_]: Monad, X, V] (r: ReaderT[Z, X, V]): StateT[Z, X, V] = {
    val ms = MonadState[StateT[Z, X, ?], X]
    for {
      s <- ms.get
      r <- liftStateT[Z, X, V] (r.run (s))
    } yield r
  }

  def run[Z[_]: Monad: Async: Logger, ClientState, TRequest, TResponse](
    action: ActionT[Z, ClientState, TRequest, TResponse], httpClient: HttpClient[Z], createStartState: () => ClientState): StateT[Z, ClientState, Result] = {
    val ms = MonadState[StateT[Z, ClientState, ?], ClientState]
    val async = Async[Z]
    val logger = Logger[Z]
    def debug (s: String) = liftStateT[Z, ClientState, Unit] (logger.debug (s))
    def info (s: String) = liftStateT[Z, ClientState, Unit] (logger.info (s))
    def err (s: String) = liftStateT[Z, ClientState, Unit] (logger.error (s))

    def runAssertions (action: ActionT[Z, ClientState, TRequest, TResponse], response: TResponse): StateT[Z, ClientState, Result] = {
      val assertions = action.assertions.map { assertion => for {
          r <- liftStateT[Z, ClientState, Result] (assertion.run(response))
          _ <- info (s"$r ${assertion.description}")
        } yield r
      }
      for {
        cs <- ms.get
        r <- assertions.sequence[StateT[Z, ClientState, ?], Result].map (Foldable[List].fold (_))
        a = action.after
        _ <- info (s"Need to apply after action:${a.isDefined}")
        newState = a.map (f => f (cs, response))
        _ <- info (s"Successfully applied after action:${newState.isDefined}")
        _ <- ms.set (newState.getOrElse (cs))
      } yield r
    }

    lazy val task: StateT[Z, ClientState, Result] = for {
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
      httpRequest <- liftStateT (action.requestMapper.run (typedRequest))

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
        case Right (httpResponse) => for {
          typedResponse <- liftStateT(action.responseMapper.run(httpResponse))
          res <- typedResponse match {
            case Failure (e) => for {
              fail <- StateT.pure[Z, ClientState, Result] (Failed)
              _ <- err (s"Failed to decode response: ${e.getMessage}")
              } yield fail
            case Success (response) => runAssertions (action, response)
          }
        } yield res
      }

      // get the state after having run all of the assertions
      afterState <- ms.get

      // apply the `lastly` task if it is defined
      lastlyState = action.lastly.map (f => f (afterState)).getOrElse (afterState)

      // write the final state
      _ <- ms.set (afterState)
    } yield result

    // Apply context handler.
    StateT[Z, ClientState, Result] { cs =>
      val r: Z[(ClientState, Result)] = task.run (cs)

      val t: (ClientState, zenith.bot.Result) = (createStartState(), Failed)

      Logger[Z].extract (r)(System.out, t)
    }
  }
}

