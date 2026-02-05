package es.eriktorr
package atm.repository

import atm.domain.model.types.{AuditEntry, TransactionState}
import atm.repository.FakeTransactionAuditor.TransactionAuditorState

import cats.effect.{IO, Ref}

import java.util.UUID

final class FakeTransactionAuditor(
    stateRef: Ref[IO, TransactionAuditorState],
) extends TransactionAuditor[IO]:
  override def createEntry(
      entry: AuditEntry,
  ): IO[Unit] =
    stateRef.flatModify: currentState =>
      val (updatedState, action) =
        currentState.entries.find(_.id == entry.id) match
          case Some(duplicated) =>
            val action = IO.raiseError(
              IllegalArgumentException(s"Transaction with id ${entry.id} already exists"),
            )
            currentState -> action
          case None =>
            val update = entry :: currentState.entries
            currentState.setAuditEntries(update) -> IO.unit
      (updatedState, action)

  override def updateState(
      id: UUID,
      newState: TransactionState,
  ): IO[Unit] =
    stateRef.flatModify: currentState =>
      val (updatedState, action) =
        currentState.entries.find(_.id == id) match
          case Some(entry) =>
            val update = entry.copy(state = newState) :: currentState.entries
            currentState.setAuditEntries(update) -> IO.unit
          case None =>
            val action = IO.raiseError(
              IllegalArgumentException(s"Transaction with id $id not found"),
            )
            currentState -> action
      (updatedState, action)

object FakeTransactionAuditor:
  final case class TransactionAuditorState(
      entries: List[AuditEntry],
  ):
    def setAuditEntries(
        newEntries: List[AuditEntry],
    ): TransactionAuditorState =
      copy(newEntries)

  object TransactionAuditorState:
    val empty: TransactionAuditorState = TransactionAuditorState(List.empty)
