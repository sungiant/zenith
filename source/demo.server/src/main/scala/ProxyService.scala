package demo.server

import demo._

import zenith._
import zenith.Context
import zenith.client._
import zenith.server._
import zenith.netty._

import cats.Monad.ops._

import org.joda.time.{DateTime, DateTimeZone}

import scala.util.Try
import scala.io.StdIn

import io.circe._
import io.circe.jawn._
import io.circe.syntax._
import io.circe.generic.auto._

final class ProxyService[Z[_]: Context] (httpClient: HttpClient[Z]) extends HttpService[Z] {

  @endpoint
  @path ("^/proxy$")
  @method (HttpMethod.POST)
  @description ("Proxies a http request.")
  def proxy (request: HttpRequest): Z[HttpResponse] = for {
    r <- request.body.flatMap (decode[ProxyRequest](_).toOption) match {
      case None => for {
        _ <- Logger[Z].debug ("Failed to JSON decode ProxyRequest")
        r <- Async[Z].success (HttpResponse.plain (400))
      } yield r
      case Some (decoded) => for {
        _ <- Logger[Z].debug (s"About to make a ${decoded.method} request to: ${decoded.url}")
        response <- httpClient.send (HttpRequest.createFromUrl (decoded.url, decoded.method))
        _ <- Logger[Z].debug (s"Received ${response.code} response from target.")
        r <- Async[Z].success (response)
      } yield r
    }
  } yield r
}

