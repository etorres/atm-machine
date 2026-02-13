package es.eriktorr
package atm.repository

import atm.domain.model.types.{AuditEntry, TransactionState}
import atm.repository.TransactionAuditorStub.TransactionAuditorState
import test.stubs.{InMemoryState, SuccessPathProviderStub}

import cats.effect.{IO, Ref}
import cats.implicits.*

import java.time.Instant
import java.util.UUID

final class TransactionAuditorStub(
    val stateRef: Ref[IO, TransactionAuditorState],
) extends TransactionAuditor[IO]
    with SuccessPathProviderStub[TransactionAuditorState, List[AuditEntry]]:
  override def createEntry(
      entry: AuditEntry,
  ): IO[Unit] =
    stateRef.flatModify: currentState =>
      val (updatedState, action) =
        currentState.value.find(_.id == entry.id) match
          case Some(duplicated) =>
            val action = IO.raiseError(
              IllegalArgumentException(
                show"Transaction with id ${entry.id} already exists",
              ),
            )
            currentState -> action
          case None =>
            val update = entry :: currentState.value
            currentState.set(update) -> IO.unit
      (updatedState, action)

  override def updateState(
      id: UUID,
      newState: TransactionState,
      at: Instant,
  ): IO[Unit] =
    stateRef.flatModify: currentState =>
      val (updatedState, action) =
        currentState.value.find(_.id == id) match
          case Some(entry) =>
            val update = entry.copy(state = newState, timestamp = at) :: currentState.value
            currentState.set(update) -> IO.unit
          case None =>
            val action = IO.raiseError(
              IllegalArgumentException(show"Transaction with id $id not found"),
            )
            currentState -> action
      (updatedState, action)

object TransactionAuditorStub:
  final case class TransactionAuditorState(
      value: List[AuditEntry],
  ) extends InMemoryState[TransactionAuditorState, List[AuditEntry]]:
    def set(
        newValue: List[AuditEntry],
    ): TransactionAuditorState =
      copy(newValue)

  object TransactionAuditorState:
    val empty: TransactionAuditorState = TransactionAuditorState(List.empty)
