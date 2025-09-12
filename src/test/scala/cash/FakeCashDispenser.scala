package es.eriktorr
package cash

import cash.Cash.{Availability, Denomination, Quantity}
import cash.CashDispenser.DispensingError
import cash.FakeCashDispenser.CashDispenserState

import cats.effect.{IO, Ref}
import cats.mtl.Raise

final class FakeCashDispenser(stateRef: Ref[IO, CashDispenserState]) extends CashDispenser[IO]:
  override def minimumUnits(
      amount: Quantity,
      availabilities: Map[Denomination, Availability],
  )(using Raise[IO, DispensingError]): IO[Map[Denomination, Quantity]] =
    stateRef.get.map(
      _.amounts.getOrElse((amount, availabilities), Map.empty),
    )

object FakeCashDispenser:
  final case class CashDispenserState(
      amounts: Map[(Quantity, Map[Denomination, Availability]), Map[Denomination, Quantity]],
  ):
    def setAmounts(
        newAmounts: Map[(Quantity, Map[Denomination, Availability]), Map[Denomination, Quantity]],
    ): CashDispenserState =
      copy(newAmounts)

  object CashDispenserState:
    val empty: CashDispenserState = CashDispenserState(Map.empty)
