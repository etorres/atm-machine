package es.eriktorr
package atm.domain

import atm.domain.CashDispenserServiceStub.{CashDispenserServiceState, Inventories}
import cash.domain.DenominationSolver
import cash.domain.model.*
import test.stubs.{FailurePathProviderStub, InMemoryState}
import test.utils.{FailureRate, FailureRateSampler}

import cats.effect.{IO, Ref}
import cats.mtl.Raise

final class CashDispenserServiceStub(
    val stateRef: Ref[IO, CashDispenserServiceState],
    val failureRateRef: Ref[IO, FailureRate],
)(using
    failureRateSampler: FailureRateSampler,
) extends CashDispenserService[IO]
    with FailurePathProviderStub[CashDispenserServiceState, Inventories]:
  override def calculateWithdrawal(
      amount: Money.Amount,
      inventory: Map[Denomination, Availability],
  )(using Raise[IO, DenominationSolver.Error]): IO[Map[Denomination, Quantity]] =
    attemptOrRaise(
      onSuccess = stateRef.get.map(
        _.value.getOrElse((amount, inventory), Map.empty),
      ),
      onFailure = DenominationSolver.Error.NotSolved,
    )

object CashDispenserServiceStub:
  type Inventories =
    Map[
      (Money.Amount, Map[Denomination, Availability]),
      Map[Denomination, Quantity],
    ]

  final case class CashDispenserServiceState(
      value: Inventories,
  ) extends InMemoryState[CashDispenserServiceState, Inventories]:
    override def set(
        newValue: Inventories,
    ): CashDispenserServiceState =
      copy(newValue)

  object CashDispenserServiceState:
    val empty: CashDispenserServiceState =
      CashDispenserServiceState(Map.empty)
