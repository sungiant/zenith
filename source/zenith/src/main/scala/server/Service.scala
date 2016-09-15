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


/**
  * Encapsulates all of the attributes of an endpoint (those pulled in from @properties).
  */
final case class EndpointAttributes (id: String, description: Option[String], method: HttpMethod, path: String)


/**
  * Endpoint
  */
final case class Endpoint[Z[_]: Monad: Async: Logger](parent: Service[Z], fn: ReflectedMethod) {

  lazy val attributes: EndpointAttributes = {
    val id = fn.getName.splitCamelCase
    val annotations = fn.getAnnotations

    val description = annotations
      .filter (x => x.annotationType == classOf[description])
      .map (x => x.asInstanceOf[description].value)
      .headOption

    val method = annotations
      .filter (x => x.annotationType == classOf[method])
      .map (x => x.asInstanceOf[method].value)
      .headOption
      .getOrElse (HttpMethod.GET)

    val path = {
      val resourcePathOpt = parent
        .getClass
        .getAnnotations
        .filter (x => x.annotationType == classOf[path])
        .map (x => x.asInstanceOf[path].value)
        .headOption

      val endpointPathOpt = annotations
        .filter (x => x.annotationType == classOf[path])
        .map (x => x.asInstanceOf[path].value)
        .headOption

      (resourcePathOpt, endpointPathOpt) match {
        case (Some (resourcePath), Some (endpointPath)) => resourcePath + endpointPath
        case (Some (resourcePath), None) => resourcePath
        case (None, Some (endpointPath)) => endpointPath
        case _ => throw new Exception ("Endpoint the parent resource and/or the endpoint itself must be tagged with the `Path` annotation.")
      }
    }

    EndpointAttributes (id, description, method, path)
  }

  def handler = new PartialFunction[HttpRequest, Z[HttpResponse]] {
    def isDefinedAt (request: HttpRequest): Boolean = request.method match {
      case x if attributes.method.toString == x =>
        attributes.path.r.findFirstMatchIn (request.path) match {
          case Some (m) => true
          case None => false
        }
      case _ => false
    }

    def apply (request: HttpRequest): Z[HttpResponse] = {
      val x = attributes.path.r.findFirstMatchIn (request.path) match {
        case Some (m) =>
          m.groupCount match {
            case 0 => fn.invoke (parent, request)
            case n =>
              val extraArgs = (1 to n).map (i => m.group (i)).toList
              fn.invoke (parent, request :: extraArgs: _*)
          }
        case None => new Exception
      }

      val a = Try { x.asInstanceOf[Z[HttpResponse]] }.toOption
      for {
        _ <- Logger[Z].log (ZENITH, DEBUG, s"executing handler for ${request.path}")
        result <- a match {
          case Some (z) => z
          case _ => throw new Exception ("Endpoint signature not supported.")
        }
      } yield result
    }
  }
}

/**
  * Encapsulates all of the attributes of the service (those pulled in from @properties).
  */
final case class ServiceAttributes (id: String, description: Option[String], endpoints: List[EndpointAttributes])


/**
 * Service
 */
abstract class Service[Z[_]: Monad: Async: Logger] {

  def requestPipeline: List[RequestFilter[Z]] = Nil
  def responsePipeline: List[ResponseMapper[Z]] = Nil

  final def handler: PartialFunction[HttpRequest, Z[HttpResponse]] = endpoints.map (_.handler).reduceLeft (_ orElse _)

  def isEndpoint (fn: ReflectedMethod): Boolean = Option (fn.getAnnotation (classOf[endpoint])).isDefined

  val endpoints: List[Endpoint[Z]] = super
    .getClass
    .getMethods
    .map (Option (_))
    .collect { case Some (fn) if isEndpoint (fn) => new Endpoint[Z] (this, fn) }
    .toList

  lazy val attributes: ServiceAttributes = {
    val id = super.getClass.getSimpleName.splitCamelCase
    val description = super
      .getClass
      .getAnnotations
      .filter (x => x.annotationType == classOf[description])
      .map (x => x.asInstanceOf[description].value)
      .headOption

    ServiceAttributes (id, description, endpoints.map (_.attributes))
  }

}
