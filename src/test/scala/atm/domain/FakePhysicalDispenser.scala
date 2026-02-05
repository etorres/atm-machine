package es.eriktorr
package atm.domain

import atm.domain.FakePhysicalDispenser.PhysicalDispenserState
import cash.domain.model.{Denomination, Quantity}

import cats.effect.{IO, Ref}

final class FakePhysicalDispenser(
    stateRef: Ref[IO, PhysicalDispenserState],
) extends PhysicalDispenser[IO]:
  override def dispense(
      notes: Map[Denomination, Quantity],
  ): IO[Unit] =
    stateRef.update: currentState =>
      currentState.setNotes(notes :: currentState.notes)

object FakePhysicalDispenser:
  final case class PhysicalDispenserState(
      notes: List[Map[Denomination, Quantity]],
  ):
    def setNotes(newNotes: List[Map[Denomination, Quantity]]): PhysicalDispenserState =
      copy(newNotes)

  object PhysicalDispenserState:
    val empty = PhysicalDispenserState(List.empty)
