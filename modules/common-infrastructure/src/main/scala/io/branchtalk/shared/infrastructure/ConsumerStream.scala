package io.branchtalk.shared.infrastructure

import cats.effect.{ ConcurrentEffect, ContextShift, Resource, Sync, Timer }
import com.typesafe.scalalogging.Logger
import fs2._

final class ConsumerStream[F[_], Event](
  consumer:  EventBusConsumer[F, Event],
  committer: EventBusCommitter[F]
) {

  // Runs pipe (projections) on events and commit them once they are processed.
  // Projections start when you run F[Unit] and stop when you exit Resource.
  def withPipeToResource[B](logger: Logger)(f: Pipe[F, Event, B])(implicit F: Sync[F]): Resource[F, F[Unit]] =
    KillSwitch.asStream[F, F[Unit]] { stream =>
      consumer
        .zip(stream)
        .flatMap {
          case (event, _) =>
            Stream(event.record.value)
              .evalTap(_ => F.delay(logger.info(s"Processing event key = ${event.record.key}")))
              .through(f)
              .map(_ => event.offset)
        }
        .through(committer)
        .compile
        .drain
    }
}
object ConsumerStream {

  def fromConfigs[F[_]: ConcurrentEffect: ContextShift: Timer, Event: SafeDeserializer[F, *]](
    busConfig:   KafkaEventBusConfig,
    consumerCfg: KafkaEventConsumerConfig
  ): ConsumerStream[F, Event] =
    new ConsumerStream(
      consumer  = KafkaEventBus.consumer[F, Event](busConfig, consumerCfg),
      committer = busConfig.toCommitBatch[F](consumerCfg)
    )
}