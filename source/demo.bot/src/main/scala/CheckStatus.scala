package demo.bot

import demo._
import zenith._, zenith.bot._
import cats.data._
import util.{Try, Success}

@description ("Check that the server side status page is running as expected")
sealed class CheckStatus[Z[_]: Context] (endpoint: String)
  extends ActionT [Z, RestClientState, HttpRequest, HttpResponse] {
  val request: ReaderT[Z, RestClientState, HttpRequest] = ReaderT { _ =>
    Async[Z].success (HttpRequest.createPlain (s"$endpoint/status"))
  }
  def requestMapper: ReaderT[Z, HttpRequest, HttpRequest] = ReaderT { x => Async[Z].success { x } }
  def responseMapper: ReaderT[Z, HttpResponse, Try[HttpResponse]] = ReaderT { x => Async[Z].success { Success (x) } }

  @assertion
  @description ("check that the response code received is 200")
  def responseCode (response: HttpResponse): ReaderT[Z, RestClientState, Result] = ReaderT { _ =>
    Async[Z].success { if (response.code == 200) Passed else Failed }
  }

  @assertion
  @description ("`all good` message exists in response body")
  def allGood (response: HttpResponse): ReaderT[Z, RestClientState, Result] = ReaderT { _ =>
    Async[Z].success {
      response.body match {
        case Some (body) =>
          body.contains("all good @ ") match {
          case true => Passed
          case false => Failed
        }
        case None => Failed
      }
    }
  }
}
