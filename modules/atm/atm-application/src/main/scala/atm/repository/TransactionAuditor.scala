package es.eriktorr
package atm.repository

import atm.domain.model.AccountId
import atm.domain.model.types.{AuditEntry, TransactionState}
import cash.domain.model.Money

import cats.effect.std.UUIDGen
import cats.effect.{Async, Clock}
import cats.implicits.*

import java.time.Instant
import java.util.UUID

trait TransactionAuditor[F[_]]:
  def createEntry(
      entry: AuditEntry,
  ): F[Unit]

  def updateState(
      id: UUID,
      newState: TransactionState,
      at: Instant,
  ): F[Unit]

object TransactionAuditor:
  extension [F[_]: {Async, Clock}](self: TransactionAuditor[F])
    def startTransaction(
        accountId: AccountId,
        money: Money,
    )(using uuidGen: UUIDGen[F]): F[UUID] =
      uuidGen.randomUUID.flatTap: txId =>
        for
          now <- Clock[F].realTimeInstant
          _ <- self.createEntry(
            AuditEntry(
              txId,
              accountId,
              money,
              TransactionState.Started,
              now,
            ),
          )
        yield ()

    def updateState(
        txId: UUID,
        newState: TransactionState,
    ): F[Unit] =
      Clock[F].realTimeInstant.flatMap: now =>
        self.updateState(txId, newState, now)
