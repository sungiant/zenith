/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith

import cats.Monad

/**
 * HttpClientProvider
 */
abstract class HttpClientProvider[Z[_]]
{
  def create (config: client.HttpClientConfig): client.HttpClient[Z]
  def getClient (): Option[client.HttpClient[Z]]
  def destroy (): Unit
}

/**
 * HttpServerProvider
 */
abstract class HttpServerProvider[Z[_]] {
  def create (config: server.HttpServerConfig[Z], plugins: List[server.HttpServerPlugin[Z]]): server.HttpServer[Z]
  def getServer (): Option[server.HttpServer[Z]]
  def destroy (): Unit
}
