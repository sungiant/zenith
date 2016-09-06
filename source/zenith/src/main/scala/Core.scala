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
import java.io.PrintStream

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
 * ContentType
 */
object ContentType {
  def guessFromPath (path: String) = {
    val p = path.toLowerCase
    if (p.endsWith (".html") || p.endsWith (".html")) "text/html"
    else if (p.endsWith (".css"))   "text/css"
    else if (p.endsWith (".js"))    "text/javascript"
    else if (p.endsWith (".csv"))   "text/csv"
    else if (p.endsWith (".xml"))   "application/xml"
    else if (p.endsWith (".json"))  "application/json"
    else if (p.endsWith (".png"))   "image/png"
    else if (p.endsWith (".jpg"))   "image/jpg"
    else if (p.endsWith (".jpeg"))  "image/jpg"
    else if (p.endsWith (".gif"))   "image/gif"
    else if (p.endsWith (".tga"))   "image/tga"
    else "text/plain"
  }

  def isPrintable (contentType: String) = contentType match {
    case x if x.contains ("text/css")
       || x.contains ("text/javascript")
       || x.contains ("text/csv")
       || x.contains ("application/xml")
       || x.contains ("application/json")
       || x.contains ("text/plain") => true
    case _ => false
  }
}

/**
 * HttpExchange
 */
final case class HttpExchange (request: HttpRequest, result: Throwable Either HttpResponse, timeMs: Int)


/**
 * HttpCommon
 *
 * Common functions between `HttpRequest` and `HttpResponse`.
 */
trait HttpCommon {
  def headers: Map[String, String]
  def data: List[Byte]
  def version: String

  lazy val contentType: Option[String] = headers.get ("Content-Type")
  lazy val charset: java.nio.charset.Charset = contentType.flatMap { ctStr =>
    "charset=([A-Za-z0-9_+-]*);?".r
      .findFirstMatchIn (ctStr)
      .flatMap (scala.util.matching.Regex.Match.unapply)
      .map (_.toUpperCase)
      .flatMap (x => Try { java.nio.charset.Charset.forName (x) }.toOption)
  }.getOrElse (java.nio.charset.Charset.forName ("UTF-8"))

  lazy val body: Option[String] = data match {
    case null | Nil => None
    case _ => Try { new String (data.toArray, charset).trim() }.toOption
  }
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
}


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
  data: List[Byte]) extends HttpCommon {
  lazy val ip: Option[String] = headers.get ("X-Forwarded-For")
  lazy val path = requestUri.split ("\\?").head
  lazy val queryString = requestUri.split ('?') match {
    case Array (_) => None
    case Array (_, query) => Some (query)
  }

  lazy val toPrettyString = {
    val lineT = s"$method $requestUri $version"
    val lineHP = s"Host: $host port $hostPort"
    val linesH = headers
      .filterNot (_._1.toLowerCase == "host")
      .map { case (k, v) => s"$k: $v" }
      .toList
    ((contentType.exists (ContentType.isPrintable), body) match {
      case (true, Some (b)) if b.size > maxPrintableMessageBodySize => lineT :: lineHP :: linesH ::: longMessageBodyReplacementText :: Nil
      case (true, Some (b)) => lineT :: lineHP :: linesH ::: b :: Nil
      case _ => lineT :: lineHP :: linesH
    }).map (x => s"--> $x").mkString ("\n")
  }

  lazy val queryParameterMap: Map[String, String] = {
    val empty = Map.empty[String, String]
    import cats.implicits._
    requestUri.split ("\\?").toList match {
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
object HttpRequest {
  def createJson     (url: String, method: String = "GET", rawBody: String = "", userHeaders: Map[String, String] = Map ()) = createEx (url, method, rawBody, userHeaders, "application/json;charset=utf-8")
  def createPlain    (url: String, method: String = "GET", rawBody: String = "", userHeaders: Map[String, String] = Map ()) = createEx (url, method, rawBody, userHeaders, "text/plain;charset=utf-8")
  
  private def createEx (url: String, method: String, rawBody: String, userHeaders: Map[String, String], contentType: String) = {
    val b = rawBody.trim ().getBytes(utf8).toList
    val h = userHeaders ++ Map ("Content-Type" -> contentType, "Content-Length" -> b.length.toString )
    create (url, method, h, b)
  }

  def create (
    url: String, method: String = "GET", headers: Map[String, String] = Map (), data: List[Byte] = Nil)
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

    val _headers = headers

    HttpRequest (method, _requestUri, "HTTP/1.1", _host, _port, _headers, data)
  }
}

/**
 * HttpResponse
 */
final case class HttpResponse (
  code: Int,
  data: List[Byte] = Nil,
  headers: Map[String, String] = Map (),
  version: String = "HTTP/1.1") extends HttpCommon {
  lazy val toPrettyString: String = {
    val lineT = s"$version $code ${HttpResponse.codes.getOrElse (code, "?")}"
    val linesH = headers.map { case (k, v) => s"$k: $v" }.toList
    ((contentType.exists (ContentType.isPrintable), body) match {
      case (true, Some (b)) if b.size > maxPrintableMessageBodySize => lineT :: linesH ::: longMessageBodyReplacementText :: Nil
      case (true, Some (b)) => lineT :: linesH ::: b :: Nil
      case _ => lineT :: linesH
    }).map (x => s"<-- $x").mkString ("\n")
  }
}
object HttpResponse {
  val codes = Map (
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
    599 -> "Network Connect Timeout Error")

  def createJson        (code: Int, rawBody: String, userHeaders: Map[String, String] = Map ()) = createEx (code, rawBody, userHeaders, "application/json;charset=utf-8")
  def createPlain       (code: Int, rawBody: String, userHeaders: Map[String, String] = Map ()) = createEx (code, rawBody, userHeaders, "text/plain;charset=utf-8")
  def createPlain       (code: Int, rawBody: String): HttpResponse = createPlain (code, rawBody, Map ())
  def createPlain       (code: Int, userHeaders: Map[String, String]): HttpResponse = createPlain (code, HttpResponse.codes (code), userHeaders)
  def createPlain       (code: Int): HttpResponse = createPlain (code, HttpResponse.codes (code), Map ())

  private def createEx (code: Int, rawBody: String, userHeaders: Map[String, String], contentType: String) = {
    val b = rawBody.trim ().getBytes(utf8).toList
    val h = userHeaders ++ Map ("Content-Type" -> contentType, "Content-Length" -> b.length.toString )
    create (code, h, b)
  }

  def create (code: Int, headers: Map[String, String] = Map (), data: List[Byte] = Nil) = HttpResponse (code, data, headers)
}

