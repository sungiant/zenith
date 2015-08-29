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



/** CONTEXTUAL TYPES **/
/**********************************************************************************************************************/

/**
 * Context
 */
trait Context[Z[_]] extends Monad[Z] with Async[Z] with Logger[Z]


/**
 * Logger
 */
@typeclass trait Logger[Z[_]] {
  def debug (msg: String): Z[Unit]
  def info (msg: String): Z[Unit]
  def warn (msg: String): Z[Unit]
  def error (msg: String): Z[Unit]
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
  def promise[T] (): Async.Promise[Z, T]
  def future[T] (x: T): Z[T]
  def success[T] (x: T): Z[T]
  def failure[T] (x: Throwable): Z[T]

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

    val path = UrlUtils.getPath (url)
    val queryString = UrlUtils.getQueryString (url)
    val fragmentIdentifier = UrlUtils.getFragmentIdentifier (url)

    val _requestUri = s"$path?$queryString#$fragmentIdentifier"

    val _host = UrlUtils.getHost (url)
    val _port = UrlUtils.getPort (url).getOrElse (80)

    val _headers = headers match {
      case None => Map[String, String]()
      case Some (x) => x }

    HttpRequest (method, _requestUri, "HTTP/1.1", _host, _port, _headers, data)
  }

  def createFromAuthority (
     authority: String, requestUri: String, // the request uri consists of path?query#fragment (this is not the same as a URI)
     method: Option[String] = None, headers: Option[Map[String, String]] = None, data: Option[String] = None)
  : HttpRequest = ???

  def createFromHostAndPort (
     host: String, port: Int, requestUri: String, // the request uri consists of path?query#fragment (this is not the same as a URI)
     method: Option[String] = None, headers: Option[Map[String, String]] = None, data: Option[String] = None)
  : HttpRequest = ???

  def getUrl (request: HttpRequest): String = s"http://${request.host}${request.requestUri}"

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

object Extensions extends StringExtensions

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


/** UTILS */
/**********************************************************************************************************************/

object ResourceUtils {
  def guessContentTypeFromPath (path: String) = {
    if (path.endsWith (".html") || path.endsWith (".html")) "text/html"
    else if (path.endsWith (".css")) "text/css"
    else if (path.endsWith (".js")) "text/javascript"
    else if (path.endsWith (".csv")) "text/csv"
    else if (path.endsWith (".xml")) "application/xml"
    else if (path.endsWith (".json")) "application/json"
    else "text/plain"
  }

  def resourceExists (resourcePath: String): Boolean = {
    try {
      val path = getClass.getResource (resourcePath).getFile
      new java.io.File (path).exists
    } catch { case _ : Throwable => false }
  }
}


/**
 * This is a standard `URL` helper, it is not a standard `URI` helper.
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
object UrlUtils {
  import scala.util.matching.Regex._

  // not including the `://`
  def getScheme (url: String): String =
    "(http|https?)(:\\/\\/)([a-zA-Z0-9-._~]+)(:|\\/?)".r.findFirstMatchIn (url).map { case Groups (one, two, three, four) => three }.get

  // not including any of the scheme
  def getHost (url: String): String = {
    val uri = url.indexOf ("://") match {
      case x if x >= 0 => url.splitAt (x)._2
      case _ => url
    }
    "([a-zA-Z0-9-._]+)".r.findFirstMatchIn (uri).map { case Groups (one) => one}.getOrElse (url)
  }

  def getPort (url: String): Option[Int] =
    "(:)([0-9]+)(/?)".r.findFirstMatchIn (url).map { case Groups (one, two, three) => two.toInt }

  def getPath (url: String): String =
    "([a-zA-Z0-9]+)(/[a-zA-Z0-9-._~/]+)".r.findFirstMatchIn (url).map { case Groups (one, two) => two }.getOrElse ("/")

  // not including the `?`
  def getQueryString (url: String): Option[String] =
    "(\\?)([a-zA-Z0-9-._~=&]+)(#?)".r.findFirstMatchIn (url).map { case Groups (one, two, three) => two }

  // not including the `#`
  def getFragmentIdentifier (url: String): Option[String] =
    "(#)([a-zA-Z0-9-._~]+)".r.findFirstMatchIn (url).map { case Groups (one, two) => two }
}


