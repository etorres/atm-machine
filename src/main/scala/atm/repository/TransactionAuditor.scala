package es.eriktorr
package atm.repository

import atm.domain.model.types.{AuditEntry, TransactionState}

import java.util.UUID

trait TransactionAuditor[F[_]]:
  def createEntry(
      entry: AuditEntry,
  ): F[Unit]

  def updateState(
      id: UUID,
      newState: TransactionState,
  ): F[Unit]
