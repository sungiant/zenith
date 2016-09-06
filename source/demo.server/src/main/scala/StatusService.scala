package demo.server

import demo._
import zenith._
import zenith.client._
import zenith.server._
import zenith.netty._
import cats.Monad.ops._
import org.joda.time.{DateTime, DateTimeZone}
import scala.util.Try
import scala.io.StdIn

final class StatusService[Z[_]: Context] () extends Service[Z] {
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
