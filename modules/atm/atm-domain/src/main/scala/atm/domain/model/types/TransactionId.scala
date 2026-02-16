package es.eriktorr
package atm.domain.model.types

import cats.effect.Async
import cats.effect.std.UUIDGen
import cats.implicits.*
import io.github.iltotore.iron.RefinedSubtype
import io.github.iltotore.iron.constraint.string.ValidUUID

import java.util.UUID

type TransactionId = TransactionId.T

object TransactionId extends RefinedSubtype[String, ValidUUID]:
  def create[F[_]: Async](using
      uuidGen: UUIDGen[F],
  ): F[T] =
    uuidGen.randomUUID.flatMap: uuid =>
      Async[F].fromEither(
        TransactionId
          .either(uuid.toString)
          .leftMap: error =>
            IllegalArgumentException(show"Invalid TransactionId: $error"),
      )

  private def from(uuid: UUID): Option[TransactionId] =
    TransactionId.option(uuid.toString)

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def unsafeFrom(uuid: UUID): TransactionId =
    from(uuid)
      .getOrElse(throw IllegalArgumentException(show"Invalid TransactionId: $uuid"))
