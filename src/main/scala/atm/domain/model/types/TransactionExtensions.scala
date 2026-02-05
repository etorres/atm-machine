package es.eriktorr
package atm.domain.model.types

import atm.repository.TransactionAuditor
import cash.domain.model.*

import cats.effect.{Async, Clock}
import cats.effect.std.UUIDGen
import cats.implicits.*

import java.util.UUID

object TransactionExtensions:
  extension [F[_]: {Async, Clock, UUIDGen}](self: TransactionAuditor[F])
    def startTransaction(
        accountId: AccountId,
        money: Money,
    ): F[UUID] =
      UUIDGen[F].randomUUID.flatTap: txId =>
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
