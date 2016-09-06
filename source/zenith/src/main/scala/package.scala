/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */

package object zenith {
  lazy val utf8 = java.nio.charset.Charset.forName ("UTF-8")
  lazy val maxPrintableMessageBodySize = 1024
  lazy val longMessageBodyReplacementText = "(message body too long to print)"
}