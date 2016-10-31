/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
package zenith.default

import cats._
import cats.data._
import cats.implicits._

import scala.util.{Try, Success, Failure}
import scala.concurrent.{Promise, Future, ExecutionContext}
import java.io.PrintStream

import zenith._
import scala.collection.immutable.HashSet

/**
 * Zenith is written in a generic way so that it can be used with different contexts.
 *
 * This package provides a functional implementation of a `Zenith Context`.
 *
 * This package is not required to use Zenith, rather it is simply an example of how one may choose
 * to implement a `Zenith Context`.
 *
 * The implementation uses Scala `Future` to provide the context's asynchronous functionality and a stack of
 * Monad Transformers to encapsulate all logging and errors.  This implementation could easily be changed to use a
 * Twitter or Akka `Future` in place of the Scala `Future`.
 */
package object context {

  // The default context is given by the following type.  It combines futures, logging and exception handling into
  // a signle HKT using Monad transformers (WriterT and XorT).
  type Type[$] = XorT[WriterT[Future, LoggingContext, ?], Throwable, $]

  // This function creates a implementation of the Zenith's `Zen` typeclass for the default
  // context type `Context`
  def typeclassImplementations (
    colourLogs: Boolean = true,
    defaultVebosity: Logger.Level = Logger.Level.DEBUG,
    verbosityOverrides: Map[Logger.Channel, Logger.Level] = Map ())(implicit
    ec: ExecutionContext) = 
  TypeclassImplementor.createBundledTypeclassImplementationsForType (
    colourLogs,
    defaultVebosity,
    verbosityOverrides)
}
