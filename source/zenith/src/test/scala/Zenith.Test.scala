/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith.test

import zenith._
import org.specs2.mutable._
import Extensions._

class HttpRequestSpec extends Specification {
  "Using HttpRequest's `createFromUrl` method" should {
    val url = "https://www.google.co.uk/webhp?sourceid=chrome-instant&ion=1&espv=2&ie=UTF-8#q=zenith"
    "provide the `host` correctly" in {
      val req = HttpRequest.createFromUrl (url)
      req.host must_== "www.google.co.uk"
    }
    "provide the `requestUri` correctly" in {
      val req = HttpRequest.createFromUrl (url)
      req.requestUri must_== "/webhp?sourceid=chrome-instant&ion=1&espv=2&ie=UTF-8#q=zenith"
    }
    "provide the `path` correctly" in {
      val req = HttpRequest.createFromUrl (url)
      req.path must_== "/webhp"
    }
  }
}