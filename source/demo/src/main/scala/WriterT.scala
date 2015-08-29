package demo

import cats.Monad.ops._
import cats.Functor.ops._
import cats._
import cats.state._
import cats.data._

/*
 * WTF! For some reason Cats doesn't define `WriterT`
 */
final case class WriterT[F[_], L, V] (run: F[(L, V)]) {
  def written (implicit f: Functor[F]): F[L] = f.map (run)(_._1)
  def value (implicit f: Functor[F]): F[V] = f.map (run)(_._2)
  def map[Z](fn: V => Z)(implicit f: Functor[F]): WriterT[F, L, Z] = WriterT {
    f.map (run) { z =>
      (z._1, fn (z._2))
    }
  }
  //Equivalent to `join (map (fa)(f))`.
  def flatMap[V2](f: V => WriterT[F, L, V2])(implicit M: Monad[F], S: Semigroup[L]): WriterT[F, L, V2] = WriterT {
    M.flatMap (run) { lv =>
      M.map (f (lv._2).run) { lv2 =>
        (S.combine (lv._1, lv2._1), lv2._2)
      }
    }
  }
}

object WriterT {

  def putT[T[_], L, V] (vt: T[V])(l: L)(implicit f: Functor[T]): WriterT[T, L, V] = WriterT (f.map (vt)(v => (l, v)))

  def put[T[_], L, V] (v: V)(l: L)(implicit f: Functor[T], a: Applicative[T]): WriterT[T, L, V] = WriterT.putT[T, L, V](a.pure (v))(l)
  def tell[T[_], L] (l: L)(implicit f: Functor[T], a: Applicative[T]): WriterT[T, L, Unit] = WriterT.put[T, L, Unit](())(l)

  def value[T[_], L, V] (v: V)(implicit f: Functor[T],  a: Applicative[T], m: Monoid[L]): WriterT[T, L, V] = WriterT.put[T, L, V](v)(m.empty)
  def valueT[T[_], L, V] (vt: T[V])(implicit f: Functor[T], m: Monoid[L]): WriterT[T, L, V] = WriterT.putT[T, L, V](vt)(m.empty)

}