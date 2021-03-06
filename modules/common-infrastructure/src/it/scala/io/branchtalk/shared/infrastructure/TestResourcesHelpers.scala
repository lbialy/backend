package io.branchtalk.shared.infrastructure

import cats.effect.Sync

import scala.util.Random

trait TestResourcesHelpers {

  def generateRandomSuffix[F[_]: Sync]: F[String] =
    Sync[F].delay("_" + LazyList.continually(Random.nextPrintableChar()).filter(_.isLetter).take(6).mkString)
}
