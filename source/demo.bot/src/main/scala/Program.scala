package demo.bot

import demo._
import zenith._, zenith.bot._, zenith.client._, zenith.netty._
import cats._, cats.data._, cats.std.all._
import scala.util.{Success, Failure}
import scala.collection.immutable.HashSet

object Program {
  def main (args: Array[String]): Unit = {
    type C[$] = defaults.CONTEXT[$]
    import java.util.concurrent.Executors
    import scala.concurrent.ExecutionContext

    val userES = Executors.newFixedThreadPool (8)
    val userEC = ExecutionContext.fromExecutorService (userES)
    implicit val context = defaults.fpContext (userEC)

    val clientProvider = new NettyHttpClientProvider[C]
    val client = clientProvider.create (HttpClientConfig ())

    val contextHandler: C[(RestClientState, Result)] => C[(RestClientState, Result)] =
      Logger[C].printAndClear[(RestClientState, Result)] (System.out, _,(RestClientState (), Failed), Logger.Level.DEBUG, HashSet.empty[Logger.Channel])

    val bot = new Bot[C, RestClientState] {
      lazy val createStartState: RestClientState = RestClientState ()
      val endpoint = "http://127.0.0.1:7777"
      val actions: List [Action[C, RestClientState]] =
        new CheckStatus[C] (endpoint) ::
        new GetLolShardMeta[C] (endpoint, "EU West") ::
        new GetLolShardDetails[C] (endpoint) ::
        Nil
    }
    val resultF = bot.run (client, contextHandler)

    def terminate (): Unit = {
      userES.shutdown ()
      sys.exit (0)
    }

    Async[C].onComplete[Result, Unit] (resultF, {
      case Success (result) =>
        result match {
          case Failed => System.out.println ("Quality Bot says `Sort it out`")
          case Passed => System.out.println ("Quality Bot says `Lookin' good`")
        }
        terminate ()
      case Failure (error) =>
        System.out.println (error)
        terminate ()
    })
  }
}
