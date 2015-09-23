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
import java.net.InetSocketAddress
import cats.Monad.ops._
import java.util.concurrent.{ExecutorService, Executors}
import scala.util.{Success, Failure}

/** NETTY UTILS */
/**********************************************************************************************************************/

private object NettyUtils {
  def toZenith (response: HttpResponse): zenith.HttpResponse = {
    val code = response.getStatus.getCode
    val version = response.getProtocolVersion.getText
    val headers = {
      import scala.collection.JavaConverters._
      val h = response.headers ()
      h.names ().asScala.map (key => (key, h.get (key))).toMap
    }
    val body = response.getContent match {
      case content if content.readable () => Some (content.toString (CharsetUtil.UTF_8))
      case _ => None
    }
    zenith.HttpResponse (code, body, headers, version)
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
      case content if content.readable () => Some (content.toString (CharsetUtil.UTF_8))
      case _ => None
    }
    zenith.HttpRequest (method, requestUri, version, host, hostPort, headers, data)
  }

  def toNetty (request: zenith.HttpRequest): HttpRequest = {
    import HttpHeaders.Names._, HttpHeaders.Values._
    val netty = new DefaultHttpRequest (HttpVersion.valueOf (request.version), HttpMethod.valueOf (request.method), request.requestUri)
    netty.setChunked (false)
    netty.headers.set (HOST, request.host)
    netty.headers.set (CONNECTION, CLOSE)
    netty.headers.set (ACCEPT_ENCODING, GZIP)
    netty.headers.set (USER_AGENT, "Netty 3.10.3.Final")
    netty.headers.set ("Client", "zenith-netty")
    request.headers.map { case (k, v) => netty.headers.set (k, v) }
    request.data.foreach { body =>
      val content = ChannelBuffers.copiedBuffer (body, CharsetUtil.UTF_8)
      netty.headers.set (CONTENT_LENGTH, String.valueOf (content.readableBytes ()))
      netty.setContent (content)
    }
    netty
  }

  def toNetty (response: zenith.HttpResponse): HttpResponse = {
    val netty = new DefaultHttpResponse (HttpVersion.valueOf (response.version), HttpResponseStatus.valueOf (response.code))
    netty.setChunked (false)
    response.headers.map { case (k, v) => netty.headers.set (k, v) }
    netty.headers.set ("Server", "nettySG")
    response.data.foreach (body => netty.setContent (ChannelBuffers.copiedBuffer (body, CharsetUtil.UTF_8)))
    netty
  }
}

import NettyUtils._

/** CLIENT */
/**********************************************************************************************************************/

private [this] final case class NettySsl (host: String, port: Int, sslCtx: SslContext)

private [this] final class NettyClientHandler[Z[_] : zenith.Context] (promise: zenith.Async.Promise[Z, zenith.HttpResponse])
  extends SimpleChannelUpstreamHandler {

  override def messageReceived (ctx: ChannelHandlerContext, e: MessageEvent): Unit = try {
    val response = e.getMessage.asInstanceOf[HttpResponse]
    if (response.isChunked) ???
    val zResponse = toZenith (response)
    promise.success (zResponse)
  } catch { case t: Throwable => promise.failure (t) }

  override def exceptionCaught (ctx: ChannelHandlerContext, e: ExceptionEvent): Unit = {
    e.getChannel.close ()
    promise.failure (e.getCause)
  }
}

private [this] final class NettyClientPipelineFactory[Z[_] : zenith.Context] (promise: zenith.Async.Promise[Z, zenith.HttpResponse], ssl: Option[NettySsl])
  extends ChannelPipelineFactory
{
  override def getPipeline: ChannelPipeline = {
    // Create a default pipeline implementation.
    val pipeline = Channels.pipeline
    ssl match {
      case Some (conf) => pipeline.addLast ("ssl", conf.sslCtx.newHandler (conf.host, conf.port))
      case _ => ()
    }
    pipeline.addLast ("codec", new HttpClientCodec ())
    pipeline.addLast ("inflater", new HttpContentDecompressor ())
    pipeline.addLast ("aggregator", new HttpChunkAggregator (1048576))
    pipeline.addLast ("handler", new NettyClientHandler (promise))
    pipeline
  }
}

final class NettyHttpClientProvider[Z[_]: zenith.Context] extends zenith.client.HttpClientProvider[Z]
{
  import java.util.concurrent.{ExecutorService, Executors}
  import NettyUtils._

  private var client: Option[zenith.client.HttpClient[Z]] = None
  private var bootstrap: ClientBootstrap = null
  private var boss: ExecutorService = null
  private var workers: ExecutorService = null

  def create (config: zenith.client.HttpClientConfig): zenith.client.HttpClient[Z] = {
    client match {
      case Some (c) => c
      case None =>
        boss = Executors.newCachedThreadPool ()
        workers = Executors.newCachedThreadPool ()
        bootstrap = new ClientBootstrap (new NioClientSocketChannelFactory (boss, workers))
        val cli = zenith.client.HttpClient[Z](send)(config)
        client = Some (cli); cli
    }
  }

  def getClient (): Option[zenith.client.HttpClient[Z]] = client

  def destroy (): Unit = {
    if (client.isDefined) {
      client = None
      bootstrap.shutdown ()
      workers.shutdown ()
      boss.shutdown ()
    }
  }

  private def send (request: zenith.HttpRequest): Z[zenith.HttpResponse] = try {
    // I solemnly swear...
    val p = zenith.Async[Z].promise[zenith.HttpResponse]()
    // Configure SSL context if necessary.
    val ssl: Option[NettySsl] = None
    // Set up the event pipeline factory.
    bootstrap.setPipelineFactory (new NettyClientPipelineFactory (p, ssl))
    // Start the connection attempt.
    val channelFuture = bootstrap.connect (new InetSocketAddress (request.host, request.hostPort))
    // Wait until the connection attempt succeeds or fails.
    val channel = channelFuture.sync ().getChannel
    // Prepare the HTTP request.
    val nettyRequest = toNetty (request)
    // Send the HTTP request.
    channel.write (nettyRequest)
    // Wait for the server to close the connection.
    channel.getCloseFuture.sync ()
    // An then...
    p.future
  } catch {
    case ex: Throwable => for {
      _ <- zenith.Logger[Z].error (s"Crap, something is wrong: ${ex.getMessage}")
      _ <- zenith.Logger[Z].error (ex.getStackTrace.toString)
    } yield zenith.HttpResponse.plain (500, "FUCK")
  }
}



/** SERVER */
/**********************************************************************************************************************/

private [this] final class NettyServerHandler[Z[_]: zenith.Context] (
    processFn: zenith.HttpRequest => Z[zenith.HttpResponse])(implicit logger: zenith.Logger[Z])
  extends SimpleChannelUpstreamHandler {

  private def nettyErrorResponse (error: String, stack: String) = toNetty (zenith.HttpResponse.json (500, s"""{ "error": "$error", "stack": "$stack" }"""))

  override def messageReceived (ctx: ChannelHandlerContext, e: MessageEvent): Unit = {
    val request = e.getMessage.asInstanceOf[HttpRequest]
    //if (request.isChunked) ???
    val sgRequest = toZenith (request)
    val zenithResponseZ: Z[zenith.HttpResponse] = processFn (sgRequest)
    zenith.Async[Z].transform (zenithResponseZ) {
      case Success (sgResponse) => toNetty (sgResponse)
      case Failure (t) => for {
        _ <- logger.error (s"[NettyServerHandler] Failed to process request $sgRequest")
      } yield nettyErrorResponse ("Netty Error ~ Failed to process request", t.getStackTrace.mkString)
    }
    .map (e.getChannel.write (_).addListener (ChannelFutureListener.CLOSE))
  }

  override def exceptionCaught (ctx: ChannelHandlerContext, e: ExceptionEvent): Unit = {
    e.getChannel.write (nettyErrorResponse ("Netty Error ~ Exception caught", e.getCause.getStackTrace.mkString)).addListener (ChannelFutureListener.CLOSE)
  }
}

private [this] final class NettyServerPipelineFactory[Z[_]: zenith.Context] (
    processFn: zenith.HttpRequest => Z[zenith.HttpResponse])
  extends ChannelPipelineFactory {
  override def getPipeline: ChannelPipeline = {
    val pipeline = Channels.pipeline
    pipeline.addLast ("decoder", new HttpRequestDecoder ())
    pipeline.addLast ("aggregator", new HttpChunkAggregator (1048576))
    pipeline.addLast ("encoder", new HttpResponseEncoder ())
    pipeline.addLast ("deflater", new HttpContentCompressor ())
    pipeline.addLast ("handler", new NettyServerHandler (processFn))
    pipeline
  }
}

final class NettyHttpServerProvider[Z[_]: zenith.Context] extends zenith.server.HttpServerProvider[Z] {
  private var server: Option[zenith.server.HttpServer[Z]] = None
  private var bootstrap: ServerBootstrap = null
  private var boss: ExecutorService = null
  private var workers: ExecutorService = null

  def getServer (): Option[zenith.server.HttpServer[Z]] = server

  def create (config: zenith.server.HttpServerConfig[Z]): zenith.server.HttpServer[Z] = {
    server match {
      case Some (s) => s
      case None =>
        boss = Executors.newCachedThreadPool ()
        workers = Executors.newCachedThreadPool ()
        bootstrap = new ServerBootstrap (new NioServerSocketChannelFactory (boss, workers))
        // Enable TCP_NODELAY to handle pipelined requests without latency.
        bootstrap.setOption ("child.tcpNoDelay", true)
        val srv = zenith.server.HttpServer[Z](config)
        // Set up the event pipeline factory.
        val channelPipelineFactory = new NettyServerPipelineFactory (srv.process)
        bootstrap.setPipelineFactory (channelPipelineFactory)
        // Bind and start to accept incoming connections.
        bootstrap.bind (new InetSocketAddress (config.port))
        server = Some (srv)
        srv
    }
  }

  def destroy (): Unit = {
    if (server.isDefined) {
      server = None
      bootstrap.shutdown ()
      workers.shutdown ()
      boss.shutdown ()
    } else ()
  }
}