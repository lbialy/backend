package io.branchtalk.api

import cats.Applicative
import cats.effect.{ ContextShift, Sync }
import com.typesafe.scalalogging.Logger
import io.branchtalk.api.JsoniterSupport.JsCodec
import io.branchtalk.api.TapirSupport.jsonBody
import sttp.model.StatusCode
import sttp.tapir.server.ServerDefaults.FailureHandling
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.{ DecodeResult, Schema, ValidationError, Validator }
import sttp.tapir.server.{ DecodeFailureContext, DecodeFailureHandling, LogRequestHandling }

object ServerOptions {

  final case class ErrorHandler[E](
    onMissing:         () => E,
    onMultiple:        () => E,
    onError:           (String, Throwable) => E,
    onMismatch:        (String, String) => E,
    onValidationError: List[ValidationError[_]] => E
  )

  private val originalHandler: DecodeFailureContext => Option[StatusCode] = FailureHandling.respondWithStatusCode(
    _,
    badRequestOnPathErrorIfPathShapeMatches = false,
    badRequestOnPathInvalidIfPathShapeMatches = true
  )
  def buildErrorHandler[E: JsCodec: Schema: Validator](
    errorHandler: ErrorHandler[E]
  ): DecodeFailureContext => DecodeFailureHandling = {
    case handled if originalHandler(handled).isEmpty =>
      DecodeFailureHandling.noMatch
    case DecodeFailureContext(_, DecodeResult.Missing) =>
      DecodeFailureHandling.response(jsonBody[E])(errorHandler.onMissing())
    case DecodeFailureContext(_, DecodeResult.Multiple(_)) =>
      DecodeFailureHandling.response(jsonBody[E])(errorHandler.onMultiple())
    case DecodeFailureContext(_, DecodeResult.Error(original, error)) =>
      DecodeFailureHandling.response(jsonBody[E])(errorHandler.onError(original, error))
    case DecodeFailureContext(_, DecodeResult.Mismatch(expected, actual)) =>
      DecodeFailureHandling.response(jsonBody[E])(errorHandler.onMismatch(expected, actual))
    case DecodeFailureContext(_, DecodeResult.InvalidValue(errors)) =>
      DecodeFailureHandling.response(jsonBody[E])(errorHandler.onValidationError(errors))
  }

  def create[F[_]: Sync: ContextShift, E: JsCodec: Schema: Validator](
    logger:       Logger,
    errorHandler: ErrorHandler[E]
  ): Http4sServerOptions[F] = {
    val debugLog: (String, Option[Throwable]) => F[Unit] = {
      case (msg, None)     => Sync[F].delay(logger.debug(msg))
      case (msg, Some(ex)) => Sync[F].delay(logger.debug(msg, ex))
    }
    Http4sServerOptions
      .default[F]
      .copy[F](
        decodeFailureHandler = buildErrorHandler(errorHandler),
        logRequestHandling = LogRequestHandling[F[Unit]](
          doLogWhenHandled = debugLog,
          doLogAllDecodeFailures = debugLog,
          doLogLogicExceptions = (msg: String, ex: Throwable) => Sync[F].delay(logger.error(msg, ex)),
          noLog = Applicative[F].unit
        )
      )
  }
}
