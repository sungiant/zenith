/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith.netty

import org.jboss.netty.util._
import org.jboss.netty.buffer._
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.nio._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.ssl._
import org.jboss.netty.bootstrap._
import cats._
import cats.data._
import cats.implicits._
import java.net.InetSocketAddress
import java.util.concurrent.{ExecutorService, Executors}
import scala.util.{Success, Failure}

private object NettyUtils {
  def toZenith (response: HttpResponse): zenith.HttpResponse = {
    val code = response.getStatus.getCode
    val version = response.getProtocolVersion.getText

    val headers = {
      import scala.collection.JavaConverters._
      val h = response.headers ()
      h.names ().asScala.map (key => (key, h.get (key))).toMap
    }

    val data = response.getContent match {
      case content if content.readable () => content.toByteBuffer.array.toList
      case _ => Nil
    }
    zenith.HttpResponse (code, data, headers, version)
  }

  def toZenith (request: HttpRequest): zenith.HttpRequest = {
    val method = request.getMethod.toString
    val requestUri = request.getUri
    val version = request.getProtocolVersion.getText
    val host = "?"
    val hostPort = -1
    val headers = {
      import scala.collection.JavaConverters._
      val h = request.headers ()
      h.names ().asScala.map (key => (key, h.get (key))).toMap
    }
    val data = request.getContent match {
      case content if content.readable () => content.toByteBuffer.array.toList
      case _ => Nil
    }
    zenith.HttpRequest (method, requestUri, version, host, hostPort, headers, data)
  }

  def toNetty (request: zenith.HttpRequest): HttpRequest = {
    import HttpHeaders.Names._, HttpHeaders.Values._
    val netty = new DefaultHttpRequest (HttpVersion.valueOf (request.version), HttpMethod.valueOf (request.method), request.requestUri)
    netty.setChunked (false)
    netty.headers.set (HOST, request.host)

    request.data match {
      case Nil => ()
      case data =>
        val bytes = data.toArray
        val content = ChannelBuffers.copiedBuffer (bytes)
        netty.headers.set (CONTENT_LENGTH, content.readableBytes ().toString)
        netty.setContent (content)
    }

    // todo: consider the fact that the user may well set these header themselves
    netty.headers.set (CONNECTION, CLOSE)
    netty.headers.set (ACCEPT_ENCODING, GZIP)
    netty.headers.set (USER_AGENT, "Netty 3.10.3.Final")
    netty.headers.set ("Client", "zenith-netty")

    // add all user headers
    request.headers.map { case (k, v) => netty.headers.set (k, v) }

    netty
  }

  def toNetty (response: zenith.HttpResponse): HttpResponse = {
    import HttpHeaders.Names._
    val netty = new DefaultHttpResponse (HttpVersion.valueOf (response.version), HttpResponseStatus.valueOf (response.code))
    netty.setChunked (false)
    response.headers.map { case (k, v) => netty.headers.set (k, v) }

    response.data match {
      case Nil => ()
      case data =>
        val bytes = data.toArray
        val content = ChannelBuffers.copiedBuffer (bytes)
        netty.headers.set (CONTENT_LENGTH, content.readableBytes ().toString)
        netty.setContent (content)
    }

    netty.headers.set (SERVER, "zenith-netty")

    netty
  }
}
