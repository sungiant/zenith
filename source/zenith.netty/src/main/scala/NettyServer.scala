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
import zenith.{Async, Logger}
import java.util.concurrent.{ExecutorService, Executors}
import scala.util.{Success, Failure}
import NettyUtils._
import zenith.netty._
import zenith.Logger.Level._

private [this] final class NettyServerHandler[Z[_]: Monad: Async: Logger](processFn: zenith.HttpRequest => Z[zenith.HttpResponse])
  extends SimpleChannelUpstreamHandler {

  private def nettyErrorResponse (msg: String, throwableOpt: Option[Throwable]) = toNetty {
    throwableOpt match {
        case None => zenith.HttpResponse.createJson (500, s"""{ "error": "Netty error - $msg" }""") 
        case Some (t) => zenith.HttpResponse.createJson (500, s"""{
            "error": "Netty error - $msg",
            "details": "${t.getMessage}",
            "stack": "${t.getCause.getStackTrace.mkString ("\n")}"
        }""")
      }
    }

  override def messageReceived (ctx: ChannelHandlerContext, e: MessageEvent): Unit = {
    val request = e.getMessage.asInstanceOf[HttpRequest]
    //if (request.isChunked) ???
    val sgRequest = toZenith (request)
    val zenithResponseZ: Z[zenith.HttpResponse] = processFn (sgRequest)
    Async[Z].transform (zenithResponseZ) {
      case Success (sgResponse) => toNetty (sgResponse)
      case Failure (t) => for {
        _ <- Logger[Z].log (LOG_CH, ERROR, s"[NettyServerHandler] Failed to process request $sgRequest")
      } yield nettyErrorResponse ("Failed to process request", Option (t))
    }.map (e.getChannel.write (_).addListener (ChannelFutureListener.CLOSE))
  }

  override def exceptionCaught (ctx: ChannelHandlerContext, e: ExceptionEvent): Unit = {
    val r = nettyErrorResponse ("Failed to process request", Option (e.getCause))
    e.getChannel.write (r).addListener (ChannelFutureListener.CLOSE)
  }
}

private [this] final class NettyServerPipelineFactory[Z[_]: Monad: Async: Logger](processFn: zenith.HttpRequest => Z[zenith.HttpResponse])
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

final class NettyHttpServerProvider[Z[_]: Monad: Async: Logger]
  extends zenith.HttpServerProvider[Z] {

  private var server: Option[zenith.server.HttpServer[Z]] = None
  private var bootstrap: ServerBootstrap = null
  private var boss: ExecutorService = null
  private var workers: ExecutorService = null

  def getServer (): Option[zenith.server.HttpServer[Z]] = server

  def create (config: zenith.server.HttpServerConfig[Z], plugins: List[zenith.server.HttpServerPlugin[Z]]): zenith.server.HttpServer[Z] = {
    server match {
      case Some (s) => s
      case None =>
        boss = Executors.newCachedThreadPool ()
        workers = Executors.newCachedThreadPool ()
        bootstrap = new ServerBootstrap (new NioServerSocketChannelFactory (boss, workers))
        // Enable TCP_NODELAY to handle pipelined requests without latency.
        bootstrap.setOption ("child.tcpNoDelay", true)
        val srv = zenith.server.HttpServer[Z](config, plugins)
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
