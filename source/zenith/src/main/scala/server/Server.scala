/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith.server

import zenith._
import zenith.Logger._
import zenith.Logger.Level._
import zenith.Extensions._
import scala.util.{Try, Success, Failure}
import cats._
import cats.data._
import org.joda.time.{DateTimeZone, DateTime}
import cats.Monad.ops._
import java.lang.reflect.{Method => ReflectedMethod}
import scala.collection.immutable.HashSet

/**
  * A plugin is just a bundled collection of services, filters and mappers.  The plugin itself does not define how
  * these elements interact with one another in a program, that kind of wiring must be done by defining facilities.
  */
abstract class Plugin[Z[_]](val identifier: String) {
  def services: List[Service[Z]] = Nil
  def requestFilters: List[RequestFilter[Z]] = Nil
  def responseMappers: List[ResponseMapper[Z]] = Nil
}

/**
  * A request filter takes a request and does one of the following:
  * - returns a response, signalling the end of processing for this request, stopping it ever reaching a handler.
  * - if everything is fine the filter returns the request, possibly with modifications (like adding additional headers)
  */
abstract class RequestFilter[Z[_]](val identifier: String) {
  def run (httpRequest: HttpRequest): Z[HttpResponse Either HttpRequest]
}

/**
  * ResponseMapper
  */
abstract class ResponseMapper[Z[_]](val identifier: String) {
  def run (httpResponse: HttpResponse): Z[HttpResponse]
}

/**
 * HttpServerConfig
 */
abstract class HttpServerConfig[Z[_]](val identifier: String, val port: Int) {
  
  def services: List[Service[Z]] = Nil
  def requestFilters: List[RequestFilter[Z]] = Nil
  def responseMappers: List[ResponseMapper[Z]] = Nil

  def requestFilterWiring: Option[(Service[Z]) => List[RequestFilter[Z]]] = None
  def responseMapperWiring: Option[(Service[Z]) => List[ResponseMapper[Z]]] = None
}

/**
 * HttpServer
 */
final case class HttpServer[Z[_]: Monad: Async: Logger](config: HttpServerConfig[Z], plugins: List[Plugin[Z]]) {

  private lazy val allServices = config.services ::: plugins.flatMap (_.services)
  private lazy val allRequestFilters = config.requestFilters ::: plugins.flatMap (_.requestFilters)
  private lazy val allResponseMappers = config.responseMappers ::: plugins.flatMap (_.responseMappers)

  def process (request: HttpRequest): Z[HttpResponse] = {
    val result = Try { processZ (request) } match {
      case Success (s) => s
      case Failure (f) => for {
        _ <- Logger[Z].log (ZENITH, ERROR, s"User `process` function failed for request: ${f.getStackTrace.mkString ("\n  ")}")
        response <- Async[Z].success { HttpResponse.createPlain (500) }
      } yield response
    }
    Logger[Z].extract (result) (System.out, HttpResponse.createPlain (500, "Something is wrong..."))
  }

  private def processZ (request: HttpRequest)
  : Z[HttpResponse] = {
    val startTime = DateTime.now (DateTimeZone.UTC).getMillis
    for {
      _ <- Logger[Z].log (ZENITH, INFO, s"Received request @ $startTime:\n${request.toPrettyString}")
      reqPath = request.path
      _ <- Logger[Z].log (ZENITH, DEBUG, s"Looking to find handler for ${request.method} $reqPath")
      response <- handlerFn (request) match {
        case Some (fn) => fn ()
        case None => for {
          _ <- Logger[Z].log (ZENITH, DEBUG, s"No suitable handler found.")
        } yield HttpResponse.createPlain (404)
      }
      endTime = DateTime.now (DateTimeZone.UTC).getMillis
      _ <- Logger[Z].log (ZENITH, INFO, s"Generated response @$endTime:\n${response.toPrettyString}")
      processingTime = endTime - startTime
      _ <- Logger[Z].log (ZENITH, INFO, s"Processing time: ${processingTime}ms")
    } yield response
  }

  private def handlerFn (request: HttpRequest)
  : Option[() => Z[HttpResponse]] = {
    allServices
      .collectFirst { case s if s.handler.isDefinedAt (request) => s }
      .map { service => () => { for {
        _ <- Logger[Z].log (ZENITH, DEBUG, s"Found handler function in service: ${service.attributes.id}")
        requestFilters = config.requestFilterWiring match {
            case Some (f) => f (service)
            case None => allRequestFilters
        }
        _ <- Logger[Z].log (ZENITH, DEBUG, s"Handler function has ${requestFilters.size} associated request filters.")
        filteredRequest <- applyRequestFilters (request, requestFilters)
        response <- filteredRequest match {
          case Left (earlyOut) => for {
            _ <- Logger[Z].log (ZENITH, DEBUG, s"Not continuing to handle request, a filter pipeline returned: $earlyOut")
            v <- Async[Z].success (earlyOut)
          } yield v
          case Right (continue) => for {
            _ <- Logger[Z].log (ZENITH, DEBUG, s"Continuing to handle request, a filter pipeline complete.")
            result <- service.handler.apply (continue)
          } yield result
        }
        responseMappers = config.responseMapperWiring match {
            case Some (f) => f (service)
            case None => allResponseMappers
        }
        _ <- Logger[Z].log (ZENITH, DEBUG, s"Handler function has ${responseMappers.size} associated response mappers.")
        mappedResponse <- applyResponseMappers (response, responseMappers)
      } yield mappedResponse
    }}
  }

  private def applyRequestFilters (req: HttpRequest, requestFilters: List[RequestFilter[Z]])
  : Z[HttpResponse Either HttpRequest] = for {
    _ <- Logger[Z].log (ZENITH, DEBUG, s"About to apply request filters")
    result <- requestFilters.foldLeft (Async[Z].success[HttpResponse Either HttpRequest] (Right (req))) { (acc, f) =>
      for {
        _ <- Logger[Z].log (ZENITH, DEBUG, s"Sequencing request filter:${f.identifier}")
        a <- acc
        result <- a match {
          case Left (bad) => Async[Z].success[HttpResponse Either HttpRequest] (Left (bad))
          case Right (good) => f.run (good)
        }
      } yield result
    }
  } yield result

  private def applyResponseMappers (resp: HttpResponse, responseMappers: List[ResponseMapper[Z]])
  : Z[HttpResponse] = for {
    _ <- Logger[Z].log (ZENITH, DEBUG, s"About to apply response mappers.")
    result <- responseMappers.foldLeft (Async[Z].success (resp)) { (acc, f) =>
      for {
        _ <- Logger[Z].log (ZENITH, DEBUG, s"Sequencing response mapper:${f.identifier}")
        a <- acc
        result <- f.run (a)
      } yield result
    }
  } yield result
}
