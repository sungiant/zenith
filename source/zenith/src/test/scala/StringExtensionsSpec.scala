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

class StringExtensionsSpec extends Specification {
  "splitCamelCase" should {
    "correctly split strings" in {
      "splitCamelCase".splitCamelCase must_== "split camel case"
    }
  }
}
