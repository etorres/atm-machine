package es.eriktorr
package atm.domain

import cash.domain.*
import cash.domain.Money.Amount

import cats.mtl.Raise

trait CashDispenserService[F[_]]:
  def calculateWithdrawal(
      amount: Amount,
      inventory: Map[Denomination, Availability],
  )(using Raise[F, DenominationSolver.Error]): F[Map[Denomination, Quantity]]

object CashDispenserService:
  def apply[F[_]](
      denominationSolver: DenominationSolver[F],
  ): CashDispenserService[F] =
    new CashDispenserService[F]:
      override def calculateWithdrawal(
          amount: Amount,
          inventory: Map[Denomination, Availability],
      )(using Raise[F, DenominationSolver.Error]): F[Map[Denomination, Quantity]] =
        denominationSolver.calculateMinimumNotes(amount, inventory)
