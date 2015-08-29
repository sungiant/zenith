/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith.client

import zenith._
import scala.util.{Success, Failure}

/**
 * HttpClientConfig
 */
final case class HttpClientConfig (timeoutMs: Int = 5000)


/**
 * HttpClientProvider
 */
abstract class HttpClientProvider[Z[_]: Context]
{
  def create (config: HttpClientConfig): HttpClient[Z]
  def getClient (): Option[HttpClient[Z]]
  def destroy (): Unit
}


/**
 * HttpClient
 */
final case class HttpClient[Z[_]: Context] (private val sendFn: HttpRequest => Z[HttpResponse])(config: HttpClientConfig) {
  def send (request: HttpRequest): Z[HttpResponse] = sendFn (request)

  def sendEx (request: HttpRequest): Z[HttpExchange] = {
    val startTime = System.nanoTime ()
    val result: Z[HttpResponse] = send (request)
    def timeMs: Int = (System.nanoTime () - startTime).toInt / 1000000
    Async[Z].transform (result) {
      case Failure (ex) => HttpExchange (request, Left (ex), timeMs)
      case Success (response) => HttpExchange (request, Right (response), timeMs)
    }
  }
}
