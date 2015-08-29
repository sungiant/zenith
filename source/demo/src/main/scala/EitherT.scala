package demo

import cats.Monad.ops._
import cats.Functor.ops._
import cats._
import cats.state._
import cats.data._

/*
 * OMFG!! For some reason Cats doesn't define `EitherT`
 */
final case class EitherT[F[_], A, B] (run: F[A Either B]) {
  def isLeft (implicit f: Functor[F]): F[Boolean] = f.map (run)(_.isLeft)
  def isRight (implicit f: Functor[F]):F[Boolean] = f.map (run)(_.isRight)

  def map[B2](fn: B => B2)(implicit f: Functor[F]): EitherT[F, A, B2] =
    EitherT (f.map [A Either B, A Either B2](run) {
      case Left (l) => Left[A, B2] (l)
      case Right (r) => Right[A, B2] (fn (r))
    })
  def flatMap[B2](fn: B => EitherT[F, A, B2])(implicit m: Monad[F]) =
    EitherT (m.flatMap [A Either B, A Either B2](run) {
      case Left (l) => m.pure (Left[A, B2] (l))
      case Right (r) => fn (r).run
    })
}

object EitherT {
  private def makeLeft[A, B]: A => A Either B = Left (_)
  private def makeRight[A, B]: B => A Either B = Right (_)
  def left[F[_], A, B] (a: F[A])(implicit f: Functor[F]): EitherT[F, A, B] = EitherT[F, A, B] (f.map (a)(makeLeft))
  def right[F[_], A, B] (b: F[B])(implicit f: Functor[F]): EitherT[F, A, B] = EitherT[F, A, B] (f.map (b)(makeRight))
}