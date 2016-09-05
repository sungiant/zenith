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
abstract class Plugin[Z[_]: Context] (val identifier: String) {
  def services: List[Service[Z]] = Nil
  def requestFilters: List[RequestFilter[Z]] = Nil
  def responseMappers: List[ResponseMapper[Z]] = Nil
}

/**
  * A request filter takes a request and does one of the following:
  * - returns a response, signalling the end of processing for this request, stopping it ever reaching a handler.
  * - if everything is fine the filter returns the request, possibly with modifications (like adding additional headers)
  */
abstract class RequestFilter[Z[_]: Context] (val identifier: String) {
  def run (httpRequest: HttpRequest): Z[HttpResponse Either HttpRequest]
}

/**
  * ResponseMapper
  */
abstract class ResponseMapper[Z[_]: Context] (val identifier: String) {
  def run (httpResponse: HttpResponse): Z[HttpResponse]
}

/**
 * HttpServerConfig
 */
abstract class HttpServerConfig[Z[_]: Context] (val identifier: String, val port: Int) {
  
  def services: List[Service[Z]] = Nil
  def requestFilters: List[RequestFilter[Z]] = Nil
  def responseMappers: List[ResponseMapper[Z]] = Nil

  def requestFilterWiring: Option[(Service[Z]) => List[RequestFilter[Z]]] = None
  def responseMapperWiring: Option[(Service[Z]) => List[ResponseMapper[Z]]] = None

  def verbosity = Logger.Level.DEBUG
  def channelFilters = HashSet.empty[Logger.Channel]
}

/**
 * HttpServer
 */
final case class HttpServer[Z[_]: Context] (config: HttpServerConfig[Z], plugins: List[Plugin[Z]])(implicit async: Async[Z], logger: Logger[Z]) {
  import logger._, async._

  private lazy val allServices = config.services ::: plugins.map (_.services).flatten
  private lazy val allRequestFilters = config.requestFilters ::: plugins.map (_.requestFilters).flatten
  private lazy val allResponseMappers = config.responseMappers ::: plugins.map (_.responseMappers).flatten

  def process (request: HttpRequest): Z[HttpResponse] = {
    val result = Try { processZ (request) } match {
      case Success (s) => s
      case Failure (f) => for {
        _ <- log (ZENITH, ERROR, s"User `process` function failed for request: ${f.getStackTrace.mkString ("\n  ")}")
        response <- Async[Z].success { HttpResponse.plain (500) }
      } yield response
    }
    Logger[Z].printAndClear (System.out, result, HttpResponse.plain (500, "Something is wrong..."), config.verbosity, config.channelFilters)
  }

  private def processZ (request: HttpRequest)
  : Z[HttpResponse] = {
    val startTime = DateTime.now (DateTimeZone.UTC).getMillis
    for {
      _ <- log (ZENITH, INFO, s"Received request @ $startTime:\n${request.toPrettyString}")
      reqPath = request.path
      _ <- log (ZENITH, DEBUG, s"Looking to find handler for ${request.method} $reqPath")
      response <- handlerFn (request) match {
        case Some (fn) => fn ()
        case None => for {
          _ <- log (ZENITH, DEBUG, s"No suitable handler found.")
        } yield HttpResponse.plain (404)
      }
      endTime = DateTime.now (DateTimeZone.UTC).getMillis
      _ <- log (ZENITH, INFO, s"Generated response @$endTime:\n${response.toPrettyString}")
      processingTime = endTime - startTime
      _ <- log (ZENITH, INFO, s"Processing time: ${processingTime}ms")
    } yield response
  }

  private def handlerFn (request: HttpRequest)
  : Option[() => Z[HttpResponse]] = {
    allServices
      .collectFirst { case s if s.handler.isDefinedAt (request) => s }
      .map { service =>
        () => { 
          for {
            _ <- log (ZENITH, DEBUG, s"Found handler function in service: ${service.attributes.id}")
            requestFilters = config.requestFilterWiring match {
                case Some (f) => f (service)
                case None => allRequestFilters
            }
            filteredRequest <- applyRequestFilters (request, requestFilters)
            response <- filteredRequest match {
              case Left (bad) => for {
                _ <- log (ZENITH, DEBUG, s"Not handling request, filters returned: $bad")
                v <- success (bad)
              } yield v
              case Right (good) => for {
                result <- service.handler.apply (good)
              } yield result
            }
            responseMappers = config.responseMapperWiring match {
                case Some (f) => f (service)
                case None => allResponseMappers
            }
            mappedResponse <- applyResponseMappers (response, responseMappers)
          } yield mappedResponse
        }
      }
  }

  private def applyRequestFilters (req: HttpRequest, requestFilters: List[RequestFilter[Z]])
  : Z[HttpResponse Either HttpRequest] = for {
    _ <- log (ZENITH, DEBUG, s"About to apply request filters")
    result <- requestFilters.foldLeft (success[HttpResponse Either HttpRequest] (Right (req))) { (acc, f) =>
      for {
        _ <- log (ZENITH, DEBUG, s"Sequencing request filter:${f.identifier}")
        a <- acc
        result <- a match {
          case Left (bad) => success[HttpResponse Either HttpRequest] (Left (bad))
          case Right (good) => f.run (good)
        }
      } yield result
    }
  } yield result

  private def applyResponseMappers (resp: HttpResponse, responseMappers: List[ResponseMapper[Z]])
  : Z[HttpResponse] = for {
    _ <- log (ZENITH, DEBUG, s"About to apply response mappers.")
    result <- responseMappers.foldLeft (success (resp)) { (acc, f) =>
      for {
        _ <- log (ZENITH, DEBUG, s"Sequencing response mapper:${f.identifier}")
        a <- acc
        result <- f.run (a)
      } yield result
    }
  } yield result
}

