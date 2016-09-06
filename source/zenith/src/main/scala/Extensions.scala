/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith

object Extensions extends StringExtensions with ThrowableExtensions

trait StringExtensions {
  implicit class Implicit (val s: String) {
    def splitCamelCase: String = {
      val a = "(?<=[A-Z])(?=[A-Z][a-z])"
      val b = "(?<=[^A-Z])(?=[A-Z])"
      val c = "(?<=[A-Za-z])(?=[^A-Za-z])"
      s"$a|$b|$c".r.replaceAllIn (s, " ").toLowerCase
    }
  }
}

trait ThrowableExtensions {
  implicit class Implicit (val t: Throwable) {
    def stackTrace: String = {
      import java.io.{PrintWriter, StringWriter}
      val stackTrace = new StringWriter
      t.printStackTrace (new PrintWriter (stackTrace))
      stackTrace.toString
    }
  }
}