package demo.bot

import demo.common._
import zenith._, zenith.bot._, zenith.client._, zenith.netty._
import cats._, cats.data._, cats.std.all._
import scala.util.{Success, Failure}
import scala.collection.immutable.HashSet
import scala.concurrent.ExecutionContext.Implicits.global

object Program {
  type C[$] = default.context.Type[$]
  type B = (RestClientState, Result)

  implicit val t = default.context.typeclassImplementations ()

  val clientProvider = new NettyHttpClientProvider[C]
  val client = clientProvider.create (HttpClientConfig ())

  val bot = new Bot[C, RestClientState] {
    lazy val createStartState: RestClientState = RestClientState ()
    val endpoint = "http://127.0.0.1:7777"
    val actions: List [Action[C, RestClientState]] =
      new CheckStatus[C] (endpoint) ::
      new GetLolShardMeta[C] (endpoint, "EU West") ::
      new GetLolShardDetails[C] (endpoint) ::
      Nil
  }

  def main (args: Array[String]): Unit = Async[C].onComplete[Result, Unit] (bot.run (client)) {
    case Success (result) =>
      result match {
        case Failed => System.out.println ("Quality Bot says `Sort it out`")
        case Passed => System.out.println ("Quality Bot says `Lookin' good`")
      }
      sys.exit (0)
    case Failure (error) =>
      System.out.println (error)
      sys.exit (0)
  }
}
