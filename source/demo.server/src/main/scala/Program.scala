package demo.server

import demo._

import zenith._
import zenith.client._
import zenith.server._
import zenith.netty._

import scala.io.StdIn

object Program {
  type C[$] = defaults.CONTEXT[$]
  import java.util.concurrent.Executors
  import scala.concurrent.ExecutionContext

  val userES = Executors.newFixedThreadPool (8)
  val userEC = ExecutionContext.fromExecutorService (userES)
  implicit val context = defaults.fpContext (userEC)

  val clientProvider = new NettyHttpClientProvider[C]
  val serverProvider = new NettyHttpServerProvider[C]

  val name = "Demo Server"
  val port = 7777

  class DemoServerConfig (client: HttpClient[C]) extends HttpServerConfig[C] (name, port) {
    override val services = new StatusService[C] () :: new ProxyService[C] (client) :: Nil
  }

  def startService (): Unit = {
    val clientConfig = HttpClientConfig ()
    val client = clientProvider.create (clientConfig)
    val serverConfig = new DemoServerConfig (client)
    val plugins: List[Plugin[C]] = new zenith.server.plugins.documentation.DocumentationPlugin[C](() => serverConfig) :: Nil
    serverProvider.create (serverConfig, plugins)
  }

  def stopService (): Unit = {
    clientProvider.destroy ()
    serverProvider.destroy ()
  }

  def main (args: Array[String]): Unit = {
    System.out.println ("Registering shutdown hook.")
    Runtime.getRuntime.addShutdownHook (new Thread {
      override def run (): Unit = {
        System.out.println (s"Shutdown hook triggered.")
        stopService ()
      }
    })
    startService ()
    System.out.println (s"$name is now running on localhost:$port")
    StdIn.readLine ()
    stopService ()
    userES.shutdown ()

    sys.exit (0)
  }
}
