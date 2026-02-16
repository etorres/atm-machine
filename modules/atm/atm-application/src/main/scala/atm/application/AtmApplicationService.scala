package es.eriktorr
package atm.application

import atm.application.AtmApplicationService.DispenseError
import atm.application.AtmApplicationService.DispenseError.{
  InsufficientFunds,
  InventoryShortage,
  RefundedError,
}
import atm.domain.model.types.{TransactionId, TransactionState}
import atm.domain.model.{AccountId, Receipt, TerminalId}
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

import java.time.{Instant, OffsetDateTime, ZoneOffset}
import scala.collection.immutable.SortedMap
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.control.NoStackTrace

trait AtmApplicationService[F[_]]:
  def withdraw(
      accountId: AccountId,
      money: Money,
  )(using Raise[F, DispenseError]): F[Receipt]

object AtmApplicationService:
  def apply[F[_]: {Async, Clock, Logger, UUIDGen}](
      atomicRepositories: AtomicCell[F, (AccountRepository[F], AtmRepository[F])],
      auditor: TransactionAuditor[F],
      dispenserService: CashDispenserService[F],
      physicalDispenser: PhysicalDispenser[F],
      terminalId: TerminalId,
      timeout: Duration = 2.seconds,
  ): AtmApplicationService[F] =
    new AtmApplicationService[F]:
      override def withdraw(
          accountId: AccountId,
          money: Money,
      )(using Raise[F, AtmApplicationService.DispenseError]): F[Receipt] =
        atomicRepositories.get
          .flatMap: (accountRepository, atmRepository) =>
            for
              _ <- accountRepository.ensureSufficientFunds(accountId, money.amount)
              inventory <- atmRepository.getAvailableCashIn(money.currency)
              withdrawal <- dispenserService.computeWithdrawal(money.amount, inventory)
              transaction <- auditor.startTransaction(accountId, money)
              _ <- transaction.withdrawWithCompensation(accountRepository, atmRepository)(
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
              receipt <- generateReceipt(
                accountId,
                money,
                transaction.txId,
              )
              _ <- transaction.updateState(TransactionState.Completed)
            yield receipt
          .timeout(timeout)

      private def generateReceipt(
          accountId: AccountId,
          money: Money,
          txId: TransactionId,
      )(using
          clock: Clock[F],
      ) =
        clock.realTimeInstant.map: nowInstant =>
          Receipt(
            transactionId = txId,
            terminalId = terminalId,
            accountId = accountId,
            money = money,
            timestamp = timestampFrom(nowInstant),
            operationType = Receipt.OperationType.Withdrawal,
          )
    end new

  def timestampFrom(
      instant: Instant,
  ): OffsetDateTime =
    OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)

  extension [F[_]: Async](self: CashDispenserService[F])
    private def computeWithdrawal(
        amount: Money.Amount,
        inventory: Map[Denomination, Availability],
    )(using Raise[F, AtmApplicationService.DispenseError]): F[Map[Denomination, Quantity]] =
      Handle
        .allow[DenominationSolver.Error]:
          self.calculateWithdrawal(amount, inventory)
        .rescue:
          case DenominationSolver.Error.NotSolved =>
            InventoryShortage(inventory.availableDenominations)
              .raise[F, Map[Denomination, Quantity]]

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

  extension [F[_]: {Async, Logger}](
      self: ActiveTransaction[F]
  )
    private def withdrawWithCompensation(
        accountRepository: AccountRepository[F],
        atmRepository: AtmRepository[F],
    )(
        accountId: AccountId,
        money: Money,
        withdrawal: Map[Denomination, Quantity],
    )(using Raise[F, DispenseError]) =
      for
        _ <- accountRepository
          .debit(accountId, money.amount)
          .flatTap: _ =>
            self.updateState(TransactionState.Debited)
        _ <- atmRepository
          .decrementInventory(money.currency, withdrawal)
          .handleErrorWith: error =>
            (for
              _ <- Logger[F].info(error)(
                show"Inventory update failed. Triggering refund for $accountId...",
              )
              _ <- self.updateState(TransactionState.Refunding)
              _ <-
                accountRepository
                  .credit(accountId, money.amount)
                  .flatTap: _ =>
                    self.updateState(TransactionState.Refunded)
                  .handleErrorWith: error =>
                    self.updateState(TransactionState.ManualInterventionRequired) >>
                      Logger[F].info(error)(show"Manual intervention needed for Tx ${self.txId}") >>
                      Async[F].raiseError(error)
            yield ()) >> RefundedError.raise[F, Unit]
      yield ()

  extension (self: Map[Denomination, Availability])
    def availableDenominations: Set[Denomination] =
      SortedMap.from(self.filter(_._2 > 0)).keySet

  sealed abstract class DispenseError(message: String)
      extends RuntimeException(message)
      with NoStackTrace

  object DispenseError:
    case object InsufficientFunds extends DispenseError("Insufficient funds in account")

    case class InventoryShortage(
        availableDenominations: Set[Denomination],
    ) extends DispenseError("Out of money")

    case object RefundedError extends DispenseError("Internal error")
