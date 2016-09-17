package demo.server

import demo._

import zenith._
import zenith.client._
import zenith.server._
import zenith.netty._

import scala.collection.immutable.HashSet
import scala.io.StdIn
import scala.concurrent.ExecutionContext.Implicits.global

object Program {
  type C[$] = default.context.Type[$]
  implicit val t = default.context.typeclassImplementations ()

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
    val plugins: List[HttpServerPlugin[C]] =
      new default.plugins.documentation.DocumentationPlugin[C](() => serverConfig) ::
      new default.plugins.fileserver.FileServerPlugin[C](getClass.getResource, getClass.getResourceAsStream, "index.html", "/web" :: Nil) ::
      Nil
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

    sys.exit (0)
  }
}
