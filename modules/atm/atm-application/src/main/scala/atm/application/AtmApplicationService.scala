package es.eriktorr
package atm.application

import atm.application.AtmApplicationService.DispenseError
import atm.application.AtmApplicationService.DispenseError.{InsufficientCash, InsufficientFunds}
import atm.domain.model.AccountId
import atm.domain.model.types.TransactionState
import atm.domain.{AccountRepository, AtmRepository, CashDispenserService, PhysicalDispenser}
import atm.repository.TransactionAuditor
import cash.domain.DenominationSolver
import cash.domain.model.*

import cats.effect.implicits.genTemporalOps
import cats.effect.std.{AtomicCell, UUIDGen}
import cats.effect.{Async, Clock}
import cats.implicits.*
import cats.mtl.implicits.given
import cats.mtl.{Handle, Raise}
import org.typelevel.log4cats.Logger

import scala.collection.immutable.SortedMap
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.control.NoStackTrace

trait AtmApplicationService[F[_]]:
  def withdraw(
      accountId: AccountId,
      money: Money,
  )(using Raise[F, DispenseError]): F[Unit]

object AtmApplicationService:
  def apply[F[_]: {Async, Clock, Logger, UUIDGen}](
      atomicRepositories: AtomicCell[F, (AccountRepository[F], AtmRepository[F])],
      auditor: TransactionAuditor[F],
      dispenserService: CashDispenserService[F],
      physicalDispenser: PhysicalDispenser[F],
      timeout: Duration = 2.seconds,
  ): AtmApplicationService[F] =
    new AtmApplicationService[F]:
      override def withdraw(
          accountId: AccountId,
          money: Money,
      )(using Raise[F, AtmApplicationService.DispenseError]): F[Unit] =
        atomicRepositories.get
          .flatMap: (accountRepository, atmRepository) =>
            for
              _ <- accountRepository.ensureSufficientFunds(accountId, money.amount)
              inventory <- atmRepository.getAvailableCashIn(money.currency)
              withdrawal <- calculateWithdrawal(inventory, money)
              _ <- (accountRepository, atmRepository, auditor)
                .withdrawWithCompensation(
                  accountId,
                  money,
                  withdrawal,
                )
              _ <- physicalDispenser
                .dispense(withdrawal)
                .handleErrorWith: error =>
                  Logger[F].error(error)(
                    show"Hardware failed after debit! Manual intervention required for $accountId",
                  ) >> Async[F].raiseError(error)
            yield ()
          .timeout(timeout)

      private def calculateWithdrawal(
          inventory: Map[Denomination, Availability],
          money: Money,
      )(using Raise[F, AtmApplicationService.DispenseError]) =
        Handle
          .allow[DenominationSolver.Error]:
            dispenserService.calculateWithdrawal(money.amount, inventory)
          .rescue:
            case DenominationSolver.Error.NotSolved =>
              InsufficientCash(inventory.availableDenominations)
                .raise[F, Map[Denomination, Quantity]]
    end new

  extension [F[_]: Async](self: AccountRepository[F])
    private def ensureSufficientFunds(
        accountId: AccountId,
        amount: Money.Amount,
    )(using Raise[F, AtmApplicationService.DispenseError]): F[Unit] =
      self
        .getBalance(accountId)
        .map(_ >= amount)
        .ifM(
          ifTrue = Async[F].unit,
          ifFalse = InsufficientFunds.raise[F, Unit],
        )

  extension [F[_]: {Async, Clock, Logger, UUIDGen}](
      self: (AccountRepository[F], AtmRepository[F], TransactionAuditor[F])
  )
    private def withdrawWithCompensation(
        accountId: AccountId,
        money: Money,
        withdrawal: Map[Denomination, Quantity],
    ): F[Unit] =
      val (accountRepository, atmRepository, auditor) = self
      for
        txId <- auditor.startTransaction(accountId, money)
        _ <- accountRepository
          .debit(accountId, money.amount)
          .flatTap: _ =>
            auditor.updateState(txId, TransactionState.Debited)
        _ <- atmRepository
          .decrementInventory(money.currency, withdrawal)
          .handleErrorWith: error =>
            for
              _ <- Logger[F].info(error)(
                show"Inventory update failed. Triggering refund for $accountId...",
              )
              _ <- auditor.updateState(txId, TransactionState.Refunding)
              _ <-
                accountRepository
                  .credit(accountId, money.amount)
                  .flatTap: _ =>
                    auditor.updateState(txId, TransactionState.Refunded)
                  .handleErrorWith: error =>
                    auditor.updateState(
                      txId,
                      TransactionState.ManualInterventionRequired,
                    ) >> Logger[F].info(error)(show"Manual intervention needed for Tx $txId")
            yield ()
      yield ()

  extension (self: Map[Denomination, Availability])
    def availableDenominations: Set[Denomination] =
      SortedMap.from(self.filter(_._2 > 0)).keySet

  sealed abstract class DispenseError(message: String)
      extends RuntimeException(message)
      with NoStackTrace

  object DispenseError:
    case class InsufficientCash(
        availableDenominations: Set[Denomination],
    ) extends DispenseError("Out of money")

    case object InsufficientFunds extends DispenseError("Insufficient funds in account")
