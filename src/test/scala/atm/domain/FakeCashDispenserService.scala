package es.eriktorr
package atm.domain

import atm.domain.FakeCashDispenserService.CashDispenserServiceState
import cash.domain.*
import cash.domain.Money.Amount

import cats.effect.{IO, Ref}
import cats.mtl.Raise

final class FakeCashDispenserService(
    stateRef: Ref[IO, CashDispenserServiceState],
) extends CashDispenserService[IO]:
  override def calculateWithdrawal(
      amount: Amount,
      inventory: Map[Denomination, Availability],
  )(using Raise[IO, DenominationSolver.Error]): IO[Map[Denomination, Quantity]] =
    stateRef.get.map(
      _.inventories.getOrElse((amount, inventory), Map.empty),
    )

object FakeCashDispenserService:
  final case class CashDispenserServiceState(
      inventories: Map[(Amount, Map[Denomination, Availability]), Map[Denomination, Quantity]],
  ):
    def setInventories(
        newInventories: Map[
          (Amount, Map[Denomination, Availability]),
          Map[Denomination, Quantity],
        ],
    ): CashDispenserServiceState =
      copy(newInventories)

  object CashDispenserServiceState:
    val empty: CashDispenserServiceState = CashDispenserServiceState(Map.empty)
