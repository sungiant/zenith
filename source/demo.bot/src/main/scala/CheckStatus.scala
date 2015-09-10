package demo.bot

import demo._
import zenith._, zenith.bot._
import cats.data._

@description ("Check that the server side status page is running as expected")
sealed class CheckStatus[Z[_]: Context] (endpoint: String)
  extends ActionT [Z, RestClientState, HttpRequest, HttpResponse] {
  val request: ReaderT[Z, RestClientState, HttpRequest] = ReaderT { _ =>
    Async[Z].success (HttpRequest.createFromUrl(s"$endpoint/status"))
  }
  def requestMapper (x: HttpRequest): HttpRequest = x
  def responseMapper (x: HttpResponse): Option[HttpResponse] = Some (x)

  @assertion
  @description ("check that the response code received is 200")
  def responseCode (response: HttpResponse): ReaderT[Z, RestClientState, Result] = ReaderT { _ =>
    Async[Z].success { if (response.code == 200) Passed else Failed }
  }

  @assertion
  @description ("`all good` message exists in response body")
  def allGood (response: HttpResponse): ReaderT[Z, RestClientState, Result] = ReaderT { _ =>
    Async[Z].success {
      response.data match {
        case Some(body) => body.contains("all good @ ") match {
          case true => Passed
          case false => Failed
        }
        case None => Failed
      }
    }
  }
}