package demo.server

import scala.collection.immutable.HashSet
import scala.io.StdIn
import scala.concurrent.ExecutionContext.Implicits.global
import org.joda.time.{DateTime, DateTimeZone}
import cats.Monad, cats.Monad.ops._
import zenith._, zenith.client._, zenith.server._, zenith.netty._
import io.circe._, io.circe.jawn._, io.circe.syntax._, io.circe.generic.semiauto._

/**********************************************************************************************************************/

final class StatusService[Z[_]: Monad: Async: Logger] () extends Service[Z] {
  @endpoint
  @path ("^/status$")
  @method (HttpMethod.GET)
  @description ("Provides information about the current health of the service.")
  def status (request: HttpRequest): Z[HttpResponse] = for {
    _ <- Logger[Z].debug ("About to process status request.")
    r <- Async[Z].success (HttpResponse.createPlain (200, s"all good @ ${DateTime.now (DateTimeZone.UTC).getMillis}"))
    _ <- Logger[Z].debug ("Finished processing status request.")
  } yield r

  @endpoint
  @path ("^/stats$")
  @method (HttpMethod.GET)
  @description ("Provides statistics about the server.")
  def stats (request: HttpRequest): Z[HttpResponse] = {
    import java.lang.management.ManagementFactory
    val runtime = Runtime.getRuntime
    val mb = 1024*1024
    val uptime = s"Uptime: ${ManagementFactory.getRuntimeMXBean.getUptime}ms"
    val usedMem = s"Used Memory: ${(runtime.totalMemory - runtime.freeMemory) / mb}mb"
    val freeMem = s"Free Memory: ${runtime.freeMemory / mb}mb"
    val totalMem = s"Total Memory: ${runtime.totalMemory / mb}mb"
    val maxMem = s"Max Memory: ${runtime.maxMemory / mb}mb"
    Async[Z].success (HttpResponse.createPlain (200, s"$uptime\n$usedMem\n$freeMem\n$totalMem\n$maxMem"))
  }
}

/**********************************************************************************************************************/

final case class ProxyRequest (url: String, method: String)
object ProxyRequest {
  implicit val je = deriveEncoder[ProxyRequest]
  implicit val jd = deriveDecoder[ProxyRequest]
}

final class ProxyService[Z[_]: Monad: Async: Logger] (httpClient: HttpClient[Z]) extends Service[Z] {
  @endpoint
  @path ("^/proxy$")
  @method (HttpMethod.POST)
  @description ("Proxies a http request.")
  def proxy (request: HttpRequest): Z[HttpResponse] = for {
    r <- request.body.flatMap (decode[ProxyRequest](_).toOption) match {
      case None => for {
        _ <- Logger[Z].debug ("Failed to JSON decode ProxyRequest")
        r <- Async[Z].success (HttpResponse.createPlain (400))
      } yield r
      case Some (decoded) => for {
        _ <- Logger[Z].debug (s"About to make a ${decoded.method} request to: ${decoded.url}")
        response <- httpClient.send (HttpRequest.createPlain (decoded.url, decoded.method))
        _ <- Logger[Z].info (s"Received response from target:\n${response.toPrettyString}")
        _ <- Logger[Z].info (s"Received response data length: ${response.data.length}")
        n = HttpResponse.createJson (200, response.body.getOrElse("{}"))
        r <- Async[Z].success (n)
        _ <- Logger[Z].info (s"About to send response data length: ${r.data.length}")
      } yield r
    }
  } yield r
}

/**********************************************************************************************************************/

object DemoServer {
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
      new default.plugins.fileserver.FileServerPlugin[C](
        getClass.getResource, getClass.getResourceAsStream, "index.html", "/web" :: Nil) ::
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
