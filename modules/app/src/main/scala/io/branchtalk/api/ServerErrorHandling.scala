package io.branchtalk.api

import cats.effect.Sync
import com.typesafe.scalalogging.Logger
import io.branchtalk.shared.model.CommonError

trait ServerErrorHandling[F[_], E] {

  def apply[A](fa: F[A]): F[Either[E, A]]
}
object ServerErrorHandling {

  def handleCommonErrors[F[_]: Sync, E](mapping: CommonError => E)(logger: Logger): ServerErrorHandling[F, E] =
    new ServerErrorHandling[F, E] {

      override def apply[A](fa: F[A]): F[Either[E, A]] = fa.map(_.asRight[E]).handleErrorWith {
        case ce:    CommonError => mapping(ce).asLeft[A].pure[F]
        case error: Throwable =>
          logger.warn("Unhandled error in domain code", error)
          error.raiseError[F, Either[E, A]]
      }
    }
}
