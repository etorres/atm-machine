package es.eriktorr
package atm.domain

import atm.domain.FakeCashDispenserService.CashDispenserServiceState
import cash.domain.DenominationSolver
import cash.domain.model.*

import cats.effect.{IO, Ref}
import cats.mtl.Raise

final class FakeCashDispenserService(
    stateRef: Ref[IO, CashDispenserServiceState],
) extends CashDispenserService[IO]:
  override def calculateWithdrawal(
      amount: Money.Amount,
      inventory: Map[Denomination, Availability],
  )(using Raise[IO, DenominationSolver.Error]): IO[Map[Denomination, Quantity]] =
    stateRef.get.map(
      _.inventories.getOrElse((amount, inventory), Map.empty),
    )

object FakeCashDispenserService:
  final case class CashDispenserServiceState(
      inventories: Map[(Money.Amount, Map[Denomination, Availability]), Map[Denomination, Quantity]],
  ):
    def setInventories(
        newInventories: Map[
          (Money.Amount, Map[Denomination, Availability]),
          Map[Denomination, Quantity],
        ],
    ): CashDispenserServiceState =
      copy(newInventories)

  object CashDispenserServiceState:
    val empty: CashDispenserServiceState = CashDispenserServiceState(Map.empty)
