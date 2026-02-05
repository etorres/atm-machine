package es.eriktorr
package atm.application

import atm.application.AtmApplicationService.Error.{InsufficientFunds, OutOfMoney}
import atm.domain.{AccountRepository, AtmRepository, CashDispenserService, PhysicalDispenser}
import cash.domain.DenominationSolver
import cash.domain.model.*

import cats.effect.Async
import cats.effect.implicits.genTemporalOps
import cats.effect.std.AtomicCell
import cats.implicits.*
import cats.mtl.implicits.given
import cats.mtl.{Handle, Raise}

import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.control.NoStackTrace

trait AtmApplicationService[F[_]]:
  def withdraw(
      accountId: AccountId,
      money: Money,
  )(using Raise[F, AtmApplicationService.Error]): F[Unit]

object AtmApplicationService:
  def apply[F[_]: Async](
      accountRepository: AccountRepository[F],
      atomicAtmRepository: AtomicCell[F, AtmRepository[F]],
      dispenserService: CashDispenserService[F],
      physicalDispenser: PhysicalDispenser[F],
      timeout: Duration = 2.seconds,
  ): AtmApplicationService[F] =
    new AtmApplicationService[F]:
      override def withdraw(
          accountId: AccountId,
          money: Money,
      )(using Raise[F, AtmApplicationService.Error]): F[Unit] =
        atomicAtmRepository.get
          .flatMap: atmRepository =>
            for
              _ <- ensureSufficientFunds(accountId, money.amount)
              inventory <- atmRepository.getAvailableCashIn(money.currency)
              withdrawal <- calculateWithdrawal(inventory, money)
              _ <- accountRepository.debit(accountId, money.amount)
              _ <- atmRepository.decrementInventory(money.currency, withdrawal)
              _ <- physicalDispenser.dispense(withdrawal)
            yield ()
          .timeout(timeout)

      private def ensureSufficientFunds(
          accountId: AccountId,
          amount: Money.Amount,
      )(using Raise[F, AtmApplicationService.Error]) =
        accountRepository
          .getBalance(accountId)
          .map(_ >= amount)
          .ifM(
            ifTrue = Async[F].unit,
            ifFalse = InsufficientFunds.raise[F, Unit],
          )

      private def calculateWithdrawal(
          inventory: Map[Denomination, Availability],
          money: Money,
      )(using Raise[F, AtmApplicationService.Error]) =
        Handle
          .allow[DenominationSolver.Error]:
            dispenserService.calculateWithdrawal(money.amount, inventory)
          .rescue:
            case DenominationSolver.Error.NotSolved =>
              OutOfMoney.raise[F, Map[Denomination, Quantity]]
    end new

  enum Error(
      val message: String,
  ) extends RuntimeException(message)
      with NoStackTrace:
    case InsufficientFunds extends Error("Insufficient funds in account")
    case OutOfMoney
        extends Error(
          "This machine does not have enough money, please go to the nearest ATM",
        )
