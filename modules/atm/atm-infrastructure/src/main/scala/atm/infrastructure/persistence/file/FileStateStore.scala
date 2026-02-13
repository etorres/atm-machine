package es.eriktorr
package atm.infrastructure.persistence.file

import atm.infrastructure.config.SystemSnapshot

import cats.effect.kernel.Async
import cats.implicits.*
import io.circe.parser.decode
import io.circe.syntax.*

import java.nio.file.{Files, Path, StandardOpenOption}

trait FileStateStore[F[_]]:
  def load(): F[SystemSnapshot]

  def dump(snapshot: SystemSnapshot): F[Unit]

object FileStateStore:
  def apply[F[_]: Async](
      filePath: Path,
  ): FileStateStore[F] =
    new FileStateStore[F]:
      override def load(): F[SystemSnapshot] =
        Async[F]
          .blocking:
            val content = Files.readString(filePath)
            decode[SystemSnapshot](content)
          .flatMap:
            case Right(snapshot) =>
              snapshot.pure[F]
            case Left(error) =>
              Async[F].raiseError(RuntimeException("Failed to load system status", error))

      override def dump(
          snapshot: SystemSnapshot,
      ): F[Unit] =
        Async[F]
          .blocking:
            val json = snapshot.asJson.spaces2
            Files.writeString(
              filePath,
              json,
              StandardOpenOption.CREATE,
              StandardOpenOption.TRUNCATE_EXISTING,
            )
          .void
