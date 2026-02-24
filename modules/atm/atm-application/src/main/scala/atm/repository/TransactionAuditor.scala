package es.eriktorr
package atm.repository

import atm.application.ActiveTransaction
import atm.domain.model.AccountId
import atm.domain.model.types.{AuditEntry, TransactionId, TransactionState}
import cash.domain.model.Money

import cats.effect.std.UUIDGen
import cats.effect.{Async, Clock}
import cats.implicits.*

import java.time.Instant

trait TransactionAuditor[F[_]: Async]:
  def createEntry(
      entry: AuditEntry,
  ): F[Unit]

  def updateState(
      id: TransactionId,
      newState: TransactionState,
      at: Instant,
  ): F[Unit]

  final def startTransaction(
      accountId: AccountId,
      money: Money,
  )(using clock: Clock[F], uuidGen: UUIDGen[F]): F[ActiveTransaction[F]] =
    (
      TransactionId.create[F],
      clock.realTimeInstant,
    ).tupled.flatMap: (txId, now) =>
      createEntry(
        AuditEntry(
          txId,
          accountId,
          money,
          TransactionState.Started,
          now,
        ),
      ).as(ActiveTransaction(txId, this))
