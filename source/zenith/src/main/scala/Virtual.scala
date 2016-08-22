/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith

/**
 * HttpClientProvider
 */
abstract class HttpClientProvider[Z[_]: Context]
{
  def create (config: client.HttpClientConfig): client.HttpClient[Z]
  def getClient (): Option[client.HttpClient[Z]]
  def destroy (): Unit
}

/**
 * HttpServerProvider
 */
abstract class HttpServerProvider[Z[_]: Context] {
  def create (config: server.HttpServerConfig[Z], plugins: List[server.Plugin[Z]]): server.HttpServer[Z]
  def getServer (): Option[server.HttpServer[Z]]
  def destroy (): Unit
}