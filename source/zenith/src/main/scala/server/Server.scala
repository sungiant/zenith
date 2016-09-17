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
abstract class HttpServerPlugin[Z[_]](val identifier: String) {
  def services: List[Service[Z]] = Nil
  def requestFilters: List[RequestFilter[Z]] = Nil
  def responseMappers: List[ResponseMapper[Z]] = Nil
}


sealed trait ApplyRequestFilterRule
object ApplyRequestFilterRule {
  object Always extends ApplyRequestFilterRule
  object IfRequestHandlerFound extends ApplyRequestFilterRule
  object IfRequestHandlerNotFound extends ApplyRequestFilterRule
}

/**
  * A request filter takes a request and does one of the following:
  * - returns a response, signalling the end of processing for this request, stopping it ever reaching a handler.
  * - if everything is fine the filter returns the request, possibly with modifications (like adding additional headers)
  */
abstract class RequestFilter[Z[_]](val identifier: String, val applyRule: ApplyRequestFilterRule) {
  def run (httpRequest: HttpRequest): Z[HttpResponse Either HttpRequest]
}

sealed trait ApplyResponseMapperRule
object ApplyResponseMapperRule {
  object Always extends ApplyResponseMapperRule
  object IfRequestHandlerFound extends ApplyResponseMapperRule
  object IfRequestHandlerNotFound extends ApplyResponseMapperRule
}


/**
  * ResponseMapper
  */
abstract class ResponseMapper[Z[_]](val identifier: String, val applyRule: ApplyResponseMapperRule) {
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
final case class HttpServer[Z[_]: Monad: Async: Logger](config: HttpServerConfig[Z], plugins: List[HttpServerPlugin[Z]]) {

  private lazy val allServices = config.services ::: plugins.flatMap (_.services)
  private lazy val allRequestFilters = config.requestFilters ::: plugins.flatMap (_.requestFilters)
  private lazy val allResponseMappers = config.responseMappers ::: plugins.flatMap (_.responseMappers)

  def process (request: HttpRequest): Z[HttpResponse] = {
    val startTime = DateTime.now (DateTimeZone.UTC).getMillis
    val result = Try {
      for {
        _ <- Logger[Z].log (ZENITH, INFO, s"Received request @ $startTime:\n${request.toPrettyString}")
        r <- processImpl (request)
        endTime = DateTime.now (DateTimeZone.UTC).getMillis
        _ <- Logger[Z].log (ZENITH, INFO, s"Generated response @$endTime:\n${r.toPrettyString}")
        processingTime = endTime - startTime
        _ <- Logger[Z].log (ZENITH, INFO, s"Processing time: ${processingTime}ms")
      } yield r
    } match {
      case Success (s) => s
      case Failure (f) => for {
        _ <- Logger[Z].log (ZENITH, ERROR, s"User `process` function failed for request: ${f.getStackTrace.mkString ("\n  ")}")
        response <- Async[Z].success { HttpResponse.createPlain (500) }
      } yield response
    }
    Logger[Z].extract (result) (System.out, HttpResponse.createPlain (500, "Something is wrong..."))
  }

  /*
    Should a RequestFilter/ResponseMapper get applied:
    
    Is there a service able to handle the request?
      Y => is there a wiring function?
        Y => is the filter contained in the list of acceptable filters for the service?
          Y => APPLY
          N => DON'T APPLY
        N => APPLY
      N => APPLY
  */
  private def processImpl (request: HttpRequest)
  : Z[HttpResponse] = {
    for {

      serviceOpt <- findHandlerService (request)

      requestFilters = serviceOpt match {
        case Some (service) => (config.requestFilterWiring match {
          case Some (f) => f (service)
          case None => allRequestFilters
        }).filterNot (_.applyRule == ApplyRequestFilterRule.IfRequestHandlerNotFound)
        case None => allRequestFilters.filterNot (_.applyRule == ApplyRequestFilterRule.IfRequestHandlerFound)
      }

      _ <- Logger[Z].log (ZENITH, DEBUG, s"Found ${requestFilters.size} suitable request filters.")
      filteredRequest <- applyRequestFilters (request, requestFilters)

      response <- filteredRequest match {
        case Left (earlyOut) => for {
          _ <- Logger[Z].log (ZENITH, DEBUG, s"Not continuing to handle request, a filter in the pipeline returned a response.")
          v <- Async[Z].success (earlyOut)
        } yield v
        case Right (continue) => for {
          _ <- Logger[Z].log (ZENITH, DEBUG, s"Continuing to handle request, filter pipeline complete.")

          handlerFnOpt = serviceOpt.map { service => () => service.handler.apply (continue) }

          response <- handlerFnOpt match {
            case Some (fn) => for {
              _ <- Logger[Z].log (ZENITH, DEBUG, s"Handler found.")
              f <- fn ()
            } yield f
            case None => for {
              _ <- Logger[Z].log (ZENITH, DEBUG, s"No suitable handler found.")
            } yield HttpResponse.createPlain (404)
          }

          responseMappers = serviceOpt match {
            case Some (service) => (config.responseMapperWiring match {
              case Some (f) => f (service)
              case None => allResponseMappers
            }).filterNot (_.applyRule == ApplyResponseMapperRule.IfRequestHandlerNotFound)
            case None => allResponseMappers.filterNot (_.applyRule == ApplyResponseMapperRule.IfRequestHandlerFound)
          }

          _ <- Logger[Z].log (ZENITH, DEBUG, s"Found ${responseMappers.size} suitable response mappers.")
          mappedResponse <- applyResponseMappers (response, responseMappers)

        } yield mappedResponse
      }
    } yield response
  }

  // Attempts to find a suitable service to handle a given request.
  private def findHandlerService (request: HttpRequest): Z[Option[Service[Z]]] = for {
    _ <- Logger[Z].log (ZENITH, DEBUG, s"Looking to find suitable service to handle ${request.method} ${request.path}}")
    serviceOpt <- Monad[Z].pure { allServices.collectFirst { case s if s.handler.isDefinedAt (request) => s } }
    _ <- serviceOpt match {
      case Some (service) => Logger[Z].log (ZENITH, DEBUG, s"Found suitable service: ${service.attributes.id}.")
      case None => Logger[Z].log (ZENITH, DEBUG, s"No suitable service found.")
    }
  } yield serviceOpt

  private def applyRequestFilters (req: HttpRequest, requestFilters: List[RequestFilter[Z]])
  : Z[HttpResponse Either HttpRequest] = for {
    _ <- Logger[Z].log (ZENITH, DEBUG, s"About to apply request filters if there are any.")
    result <- requestFilters.foldLeft (Async[Z].success[HttpResponse Either HttpRequest] (Right (req))) { (acc, f) =>
      for {
        _ <- Logger[Z].log (ZENITH, DEBUG, s"Sequencing request filter: ${f.identifier}")
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
    _ <- Logger[Z].log (ZENITH, DEBUG, s"About to apply response mappers if there are any.")
    result <- responseMappers.foldLeft (Async[Z].success (resp)) { (acc, f) =>
      for {
        _ <- Logger[Z].log (ZENITH, DEBUG, s"Sequencing response mapper: ${f.identifier}")
        a <- acc
        result <- f.run (a)
      } yield result
    }
  } yield result
}
