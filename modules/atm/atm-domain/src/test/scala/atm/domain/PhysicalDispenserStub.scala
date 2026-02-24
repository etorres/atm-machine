package es.eriktorr
package atm.domain

import atm.domain.PhysicalDispenserStub.{DispensedNotes, PhysicalDispenserState}
import cash.domain.model.{Denomination, Quantity}
import test.stubs.{FailurePathProviderStub, InMemoryState}
import test.utils.{FailureRate, FailureRateSampler, SimulatedFailure}

import cats.effect.{IO, Ref}

final class PhysicalDispenserStub(
    val stateRef: Ref[IO, PhysicalDispenserState],
    val failureRateRef: Ref[IO, FailureRate],
)(using
    failureRateSampler: FailureRateSampler,
) extends PhysicalDispenser[IO]
    with FailurePathProviderStub[PhysicalDispenserState, DispensedNotes]:
  override def dispense(
      notes: Map[Denomination, Quantity],
  ): IO[Unit] =
    attemptOrRaiseError(
      onSuccess = stateRef.update: currentState =>
        currentState.set(notes :: currentState.value),
      onFailure = SimulatedFailure,
    )

object PhysicalDispenserStub:
  type DispensedNotes = List[Map[Denomination, Quantity]]

  final case class PhysicalDispenserState(
      value: DispensedNotes,
  ) extends InMemoryState[PhysicalDispenserState, DispensedNotes]:
    override def set(newValue: DispensedNotes): PhysicalDispenserState =
      copy(newValue)

  object PhysicalDispenserState:
    val empty = PhysicalDispenserState(List.empty)
