/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith

import simulacrum._
import scala.util.{Try, Success, Failure}
import cats.Monad
import zenith.Extensions._


/** CONTEXTUAL TYPES **/
/**********************************************************************************************************************/

/**
 * Context
 */
trait Context[Z[_]] extends Monad[Z] with Async[Z] with Logger[Z]
object Context {
  implicit def apply[Z[_]] (implicit m: Monad[Z], a: Async[Z], l: Logger[Z]): Context[Z] = {
    new Context[Z] with Monad[Z] with Async[Z] with Logger[Z] {
      /** Logger */
      override def log (channel: => Option[String], level: => zenith.Logger.Level, message: => String): Z[Unit] = l.log (channel, level, message)
      /** Async */
      override def liftScalaFuture[T] (expression: => scala.concurrent.Future[T]): Z[T] = a.liftScalaFuture (expression)
      override def future[T] (expression: => T): Z[T] = a.future (expression)
      override def success[T] (expression: => T): Z[T] =  a.success (expression)
      override def failure[T] (expression: => Throwable): Z[T] = a.failure (expression)
      override def onComplete[T, X](v: Z[T], f: Try[T] => X): Unit = a.onComplete (v, f)
      override def promise[T] (): Async.Promise[Z, T] = a.promise ()
      /** Monad */
      override def pure[A] (a: A): Z[A] = m.pure (a)
      override def flatMap[A, B] (fa: Z[A])(f: A => Z[B]): Z[B] = m.flatMap(fa)(f)
      override def ap[A, B] (fa: Z[A])(ff: Z[A => B]): Z[B] = m.ap (fa)(ff)
    }
  }
}


/**
 * Logger
 */
@typeclass trait Logger[Z[_]] {
  import Logger._
  def log (channel: => Option[String], level: => Level, message: => String): Z[Unit]

  /* HELPERS */
  def log (channel: => Option[String], level: => Level, throwable: Throwable): Z[Unit] = log (channel, level, throwable.stackTrace)
  def debug (message: => String): Z[Unit] = log (None, Level.DEBUG, message)
  def info (message: => String): Z[Unit] = log (None, Level.INFO, message)
  def warn (message: => String): Z[Unit] = log (None, Level.WARN, message)
  def error (message: => String): Z[Unit] = log (None, Level.ERROR, message)
  def trace (throwable: => Throwable): Z[Unit] = log (None, Level.ERROR, throwable)
}
object Logger {
  val ZENITH = Some ("ZENITH")
  case class Level (value: Int, name: String)
  object Level {
    val DEBUG = Level (1, "debug")
    val INFO = Level (2, "info")
    val WARN = Level (3, "warn")
    val ERROR = Level (4, "error")

    private val levels = List (DEBUG, INFO, WARN, ERROR)

    def apply(value: Int): Option[Level] = levels find (_.value == value)
    def apply(name: String): Option[Level] = levels find (_.name == name.toUpperCase)
  }
}


/**
 * Async
 *
 * This typeclass is an abstraction over common features available in different flavours of `Future`, like:
 * - scala.concurrent.Future
 * - com.twitter.util.Future with
 * - scala.actor.Future
 * - java.util.concurrent.Future
 *
 */
@typeclass trait Async[Z[_]] {

  def promise[T](): Async.Promise[Z, T]
  def future[T] (expression: => T): Z[T]
  def success[T] (expression: => T): Z[T]
  def failure[T] (expression: => Throwable): Z[T]

  def liftScalaFuture[T] (fx: => scala.concurrent.Future[T]): Z[T]


  /*
   * When this future is completed, either through an exception, or a value,
   *  apply the provided function.
   *
   *  If the future has already been completed, this will either be applied immediately or be scheduled asynchronously.
   */
  def onComplete[X, Y](v: Z[X], fn: Try[X] => Y): Unit

  /*
   * Async types can contain exceptions.
   * When we do a normal `map`, we only map over the success case.  This
   * function allows access to both the success or the failure case.
   */
  def transform[A, B] (v: Z[A])(fn: Try[A] => B): Z[B] = {
    val p = promise[B]()
    onComplete[A, Unit] (v, vResult =>
      // Need a try here because `fn` may throw exceptions itself.
      try {
        val result = fn (vResult)
        p.success (result)
      } catch { case t: Throwable => p.failure (t) })

    p.future
  }
}
object Async {
  trait Promise[Z[_], T] {
    def success (x: T): Unit
    def failure (x: Throwable): Unit
    def future: Z[T]
  }
}



/** REQUEST & RESPONSE TYPES **/
/**********************************************************************************************************************/
/**
 * http://en.wikipedia.org/wiki/Uniform_resource_locator
 *
 * URI scheme
 *
 *  foo://username:password@example.com:8042/over/there/index.dtb?type=animal&name=narwhal#nose
 *  \_/   \_______________/ \_________/ \__/            \___/ \_/ \______________________/ \__/
 *   |           |               |       |                |    |            |                |
 *   |       userinfo           host    port              |    |          query          fragment
 *   |    \________________________________/\_____________|____|/ \__/        \__/
 * scheme                 |                          |    |    |    |          |
 *  name              authority                      |    |    |    |          |
 *   |                                             path   |    |    interpretable as keys
 *   |                                                    |    |
 *   |    \_______________________________________________|____|/       \____/     \_____/
 *   |                         |                          |    |          |           |
 * scheme              hierarchical part                  |    |    interpretable as values
 *  name                                                  |    |
 *   |            path               interpretable as filename |
 *   |   ___________|____________                              |
 *  / \ /                        \                             |
 *  urn:example:animal:ferret:nose               interpretable as extension
 *
 *                path
 *         _________|________
 * scheme /                  \
 *  name  userinfo  hostname       query
 *  _|__   ___|__   ____|____   _____|_____
 * /    \ /      \ /         \ /           \
 * mailto:username@example.com?subject=Topic
 *
 *
 * (diagram from: http://en.wikipedia.org/wiki/URI_scheme#Examples)
 *
 */

/**
 * HttpExchange
 */
final case class HttpExchange (request: HttpRequest, result: Throwable Either HttpResponse, timeMs: Int)


/**
 * HttpRequest
 *
 * HTTP REQUEST HEADER FORMAT
 *
 * GET /DescribeNodes?NodeTypeId=6&LanguageCode=ES HTTP/1.1
 * Host: localhost:8092
 *
 * 1st line ~ Method Route Version
 * 2nd line ~ Host:Port
 *
 */
final case class HttpRequest (
  // HTTP LINE #1
  method: String, // GET, POST and so on...
  requestUri: String, // the request uri consists of path?query#fragment (this is not the same as a URI)
  version: String,
  // HTTP LINE #2
  host: String, // example.com. WARNING: Does not include the `HTTP://` part (the scheme)
  hostPort: Int, // this is really part of the host, nicer in it's own value though.
  // HTTP HEADER LINES
  headers: Map[String, String],
  // HTTP PAYLOAD
  data: Option[String]) {
  lazy val ip: Option[String] = headers.get ("X-Forwarded-For")
  lazy val cookies: Map[String, String] = {
    def removeLeadingAndTrailingWhitespace (s: String): String = s.replaceAll ("""^\s+(?m)""","")
    headers.get ("Cookie").map { cookieString =>
      cookieString.split (';').toList.map (removeLeadingAndTrailingWhitespace).map { rawCookie =>
        val split = rawCookie.split ('=').toList
        split match {
          case one :: two :: Nil => Some ((one, two))
          case _ => None
        }
      }
    }.map (z => z.collect { case Some (x) => x }.toMap).getOrElse (Map ())
  }
  lazy val path = requestUri.split ("\\?").head

  lazy val queryString = requestUri.split ('?') match {
    case Array (_) => None
    case Array (_, query) => Some (query)
  }

  def toPrettyString = {
    s"--> $method $requestUri $version\n" +
    s"--> Host: $host port $hostPort" +
    headers
      .filterNot (_._1.toLowerCase == "host")
      .foldLeft ("") { (a, i) => s"\n--> ${i._1}: ${i._2}"} +
    data.map (x => s"\n--> $x").getOrElse ("")
  }
}
object HttpRequest {
  def createFromUrl (
    url: String, method: String = "GET", headers: Option[Map[String, String]] = None, data: Option[String] = None)
  : HttpRequest = {
    import scala.util.matching.Regex._
    val jURL = new java.net.URL (url)

    val _path = jURL.getPath
    val _query =  "(\\?)([a-zA-Z0-9-._=&~]+)".r.findFirstMatchIn (url).map { case Groups (one, two) => two }
    val _fragment = "(#)([a-zA-Z0-9-._=&~]+)".r.findFirstMatchIn (url).map { case Groups (one, two) => two }

    // Not what we want: jURL.getPath.toString
    val _requestUri = (_query, _fragment) match {
      case (Some (q), Some (f)) =>  _path + s"?$q#$f"
      case (Some (q), None) =>      _path + s"?$q"
      case (None, Some (f)) =>      _path + s"#$f"
      case (None, None) =>          _path
    }

    val _host = jURL.getHost
    val _port = jURL.getPort match {
      case x if x < 0 => 80
      case x => x
    }

    val _headers = headers match {
      case None => Map[String, String]()
      case Some (x) => x }

    HttpRequest (method, _requestUri, "HTTP/1.1", _host, _port, _headers, data)
  }

  def getQueryParameterMap (request: HttpRequest): Map[String, String] = {
    val empty = Map.empty[String, String]
    import cats.implicits._
    request.requestUri.split ("\\?").toList match {
      case (head :: tail :: Nil) =>
        tail
          .split ("&")
          .toList
          .map (x => x.split ("=").toList)
          .collect { case k :: v :: Nil => Map (k -> v) }
          .foldLeft (empty)((x, i) => x |+| i)
      case _ => empty
    }
  }
}


/**
 * HttpResponse
 */
final case class HttpResponse (code: Int, data: Option[String] = None, headers: Map[String, String] = Map (), version: String = "HTTP/1.1") {
  def toPrettyString =
    s"<-- $version $code ${HttpResponse.codes.getOrElse (code, "?")}" +
    headers.foldLeft ("") { (a, i) => s"\n<-- ${i._1}: ${i._2}"} +
    data.map (x => s"\n<-- $x").getOrElse ("")
}
object HttpResponse {
  def json (code: Int, body: String, headers: Map[String, String]) =        HttpResponse (code, Some (body), headers ++ Map ("Content-Type" -> "application/json;charset=utf-8"))
  def xml (code: Int, body: String, headers: Map[String, String]) =         HttpResponse (code, Some (body), headers ++ Map ("Content-Type" -> "application/xml;charset=utf-8"))
  def js (code: Int, body: String, headers: Map[String, String]) =          HttpResponse (code, Some (body), headers ++ Map ("Content-Type" -> "text/javascript;charset=utf-8"))
  def html (code: Int, body: String, headers: Map[String, String]) =        HttpResponse (code, Some (body), headers ++ Map ("Content-Type" -> "text/html;charset=utf-8"))
  def css (code: Int, body: String, headers: Map[String, String]) =         HttpResponse (code, Some (body), headers ++ Map ("Content-Type" -> "text/css;charset=utf-8"))
  def csv (code: Int, body: String, headers: Map[String, String]) =         HttpResponse (code, Some (body), headers ++ Map ("Content-Type" -> "text/csv;charset=utf-8"))
  def plain (code: Int, body: String, headers: Map[String, String]) =       HttpResponse (code, Some (body), headers ++ Map ("Content-Type" -> "text/plain;charset=utf-8"))
  def formEncoded (code: Int, body: String, headers: Map[String, String]) = HttpResponse (code, Some (body), headers ++ Map ("Content-Type" -> "text/xml;charset=utf-8"))

  def json (code: Int, body: String): HttpResponse = json (code, body, Map ())
  def xml (code: Int, body: String): HttpResponse = xml (code, body, Map ())
  def js (code: Int, body: String): HttpResponse = js (code, body, Map ())
  def html (code: Int, body: String): HttpResponse = html (code, body, Map ())
  def css (code: Int, body: String): HttpResponse = css (code, body, Map ())
  def csv (code: Int, body: String): HttpResponse = csv (code, body, Map ())
  def plain (code: Int, body: String): HttpResponse = plain (code, body, Map ())
  def formEncoded (code: Int, body: String): HttpResponse = formEncoded (code, body, Map ())

  def json (code: Int): HttpResponse = json (code, codes (code), Map ())
  def xml (code: Int): HttpResponse = xml (code, codes (code), Map ())
  def js (code: Int): HttpResponse = js (code, codes (code), Map ())
  def html (code: Int): HttpResponse = html (code, codes (code), Map ())
  def css (code: Int): HttpResponse = css (code, codes (code), Map ())
  def csv (code: Int): HttpResponse = csv (code, codes (code), Map ())
  def plain (code: Int): HttpResponse = plain (code, codes (code), Map ())
  def formEncoded (code: Int): HttpResponse = formEncoded (code, codes (code), Map ())


  private val codes = Map (
    100 -> "Continue", 101 -> "Switching Protocols", 102 -> "Processing",

    200 -> "OK", 201 -> "Created", 202 -> "Accepted", 203 -> "Non-Authoritative Information", 204 -> "No Content",
    205 -> "Reset Content", 206 -> "Partial Content", 207 -> "Multi-Status", 208 -> "Already Reported",
    226 -> "IM Used",

    300 -> "Multiple Choices", 302 -> "Found", 303 -> "See Other", 304 -> "Not Modified", 305 -> "Use Proxy",
    306 -> "Switch Proxy", 307 -> "Temporary Redirect", 308 -> "Permanent Redirect",

    400 -> "Bad Request", 401 -> "Unauthorized", 402 -> "Payment Required", 403 -> "Forbidden", 404 -> "Not Found",
    405 -> "Method Not Allowed", 406 -> "Not Acceptable", 407 -> "Proxy Authentication Required",
    408 -> "Request Timeout", 409 -> "Conflict", 410 -> "Gone", 411 -> "Length Required", 418 -> "I'm a teapot",

    500 -> "Internal Server Error", 501 -> "Not Implemented", 502 -> "Bad Gateway", 503 -> "Service Unavailable",
    504 -> "Gateway Timeout", 505 -> "HTTP Version Not Supported", 506 -> "Variant Also Negotiates",
    507 -> "Insufficient Storage", 508 -> "Loop Detected", 509 -> "Bandwidth Limit Exceeded", 510 -> "Not Extended",
    511 -> "Network Authentication Required", 520 -> "Unknown Error", 598 -> "Network Read Timeout Error",
    599 -> "Network Connect Timeout Error"
  )
}


/** EXTENSIONS */
/**********************************************************************************************************************/

object Extensions extends StringExtensions with ThrowableExtensions

trait StringExtensions {
  implicit class Implicit (val s: String) {
    def splitCamelCase: String = {
      val a = "(?<=[A-Z])(?=[A-Z][a-z])"
      val b = "(?<=[^A-Z])(?=[A-Z])"
      val c = "(?<=[A-Za-z])(?=[^A-Za-z])"
      s"$a|$b|$c".r.replaceAllIn (s, " ")
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


/** UTILS */
/**********************************************************************************************************************/

object ResourceUtils {
  def guessContentTypeFromPath (path: String) = {
    val p = path.toLowerCase
    if (p.endsWith (".html") || p.endsWith (".html")) "text/html"
    else if (p.endsWith (".css")) "text/css"
    else if (p.endsWith (".js")) "text/javascript"
    else if (p.endsWith (".csv")) "text/csv"
    else if (p.endsWith (".xml")) "application/xml"
    else if (p.endsWith (".json")) "application/json"
    else if (p.endsWith (".png")) "image/png"
    else if (p.endsWith (".jpg")) "image/jpg"
    else if (p.endsWith (".jpeg")) "image/jpg"
    else if (p.endsWith (".gif")) "image/gif"
    else if (p.endsWith (".tga")) "image/tga"
    else "text/plain"
  }

  def resourceExists (resourcePath: String): Boolean = {
    try {
      val path = getClass.getResource (resourcePath).getFile
      new java.io.File (path).exists
    } catch { case _ : Throwable => false }
  }
}
