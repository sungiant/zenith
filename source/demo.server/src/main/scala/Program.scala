package demo.server

import demo._

import zenith._
import zenith.client._
import zenith.server._
import zenith.netty._

import scala.io.StdIn

object Program {
  type C[$] = FunctionalContext.CONTEXT[$]
  import java.util.concurrent.Executors
  import scala.concurrent.ExecutionContext

  val userES = Executors.newFixedThreadPool (8)
  val userEC = ExecutionContext.fromExecutorService (userES)
  implicit val context: Context[C] = FunctionalContext.context (userEC)

  val clientProvider = new NettyHttpClientProvider[C]
  val serverProvider = new NettyHttpServerProvider[C]

  val name = "Demo Server"
  val p = 7777

  def genServerConfig (client: HttpClient[C]): HttpServerConfig[C] = {
    val statusService = new StatusService[C] ()
    val proxyService = new ProxyService[C] (client)
    new HttpServerConfig[C] {
      val identifier = name
      val port = p
      val serviceGroups = HttpServiceGroup[C](statusService :: proxyService :: Nil) :: Nil
      override val resourcePaths = "/" :: Nil
      override def contextHandler (z: C[HttpResponse]) = FunctionalContext.handle[HttpResponse] (userEC, HttpResponse.plain (500)) (z)
    }
  }

  def startService (): Unit = {
    val clientConfig = HttpClientConfig ()
    val client = clientProvider.create (clientConfig)
    val serverConfig = genServerConfig (client)
    serverProvider.create (serverConfig)
  }

  def stopService (): Unit = {
    clientProvider.destroy ()
    serverProvider.destroy ()
  }

  def main (args: Array[String]): Unit = {
    println ("Registering shutdown hook.")
    Runtime.getRuntime.addShutdownHook (new Thread {
      override def run (): Unit = {
        println (s"Shutdown hook triggered.")
        stopService ()
      }
    })
    startService ()
    println (s"$name is now running on localhost:$p")
    StdIn.readLine ()
    stopService ()
    userES.shutdown ()

    sys.exit (0)
  }
}
