package es.eriktorr
package atm.application

import atm.domain.model.types.{TransactionId, TransactionState}
import atm.repository.TransactionAuditor

import cats.effect.{Async, Clock}
import cats.implicits.*

final case class ActiveTransaction[F[_]: {Clock, Async}](
    txId: TransactionId,
    private val auditor: TransactionAuditor[F],
):
  def updateState(
      newState: TransactionState,
  ): F[Unit] =
    Clock[F].realTimeInstant.flatMap: now =>
      auditor.updateState(txId, newState, now)
