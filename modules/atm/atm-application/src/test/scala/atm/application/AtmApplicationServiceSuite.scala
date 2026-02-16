package es.eriktorr
package atm.application

import atm.application.AtmApplicationService.{availableDenominations, DispenseError}
import atm.application.AtmApplicationServiceSuite.*
import atm.application.gen.AtmGenerators
import atm.application.support.AtmTestRunner.Stubs
import atm.application.support.{AtmTestAudit, AtmTestRunner}
import atm.domain.model.types.TransactionState
import test.utils.FailureRate.alwaysFailed
import test.utils.SimulatedFailure

import cats.effect.IO
import cats.implicits.*
import org.scalacheck.effect.PropF.forAllF

final class AtmApplicationServiceSuite extends AtmTestRunner with AtmGenerators with AtmTestAudit:
  test("should withdraw an amount of money in the given currency"):
    forAllF(withdrawalTestCaseGen):
      case (terminalId, accountId, money, initialState, Some(expectedReceipt)) =>
        withService(terminalId, initialState): (atmApplicationService, _) =>
          atmApplicationService
            .withdraw(accountId, money)
            .assertEquals(expectedReceipt)
        .map: (_, finalState) =>
          verifyFundsInvariants(accountId, money, initialState, finalState)

  test("should return an error when the account has insufficient funds"):
    forAllF(insufficientFundsTestCaseGen):
      case (terminalId, accountId, money, initialState, None) =>
        withService(terminalId, initialState): (atmApplicationService, _) =>
          atmApplicationService.withdraw(accountId, money)
        .mapOrFail:
          case (Left(error: DispenseError.InsufficientFunds.type), finalState) => finalState
        .map:
          verifyFundsUnchanged(accountId, money, initialState, _)

  test("should return an error when the ATM does not have enough money"):
    forAllF(outOfMoneyTestCaseGen):
      case (terminalId, accountId, money, initialState, None) =>
        withService(terminalId, initialState): (atmApplicationService, stubs) =>
          stubs.failingToDispenseCash
            *> atmApplicationService.withdraw(accountId, money)
        .mapOrFail:
          case (Left(DispenseError.InsufficientCash(availableDenominations)), finalState) =>
            val finalDenominations = finalState.atmRepositoryState.value
              .getOrElse(money.currency, Map.empty)
              .availableDenominations
            (availableDenominations, finalDenominations, finalState)
        .map: (availableDenominations, finalDenominations, finalState) =>
          assert(finalDenominations == availableDenominations, "Available denominations")
          verifyFundsUnchanged(accountId, money, initialState, finalState)

  test("should refund withdrawn money if cash inventory update fails"):
    forAllF(withdrawalTestCaseGen):
      case (terminalId, accountId, money, initialState, _) =>
        withService(terminalId, initialState): (atmApplicationService, stubs) =>
          stubs.failingToDecrementCashInventory
            *> atmApplicationService.withdraw(accountId, money)
        .mapOrFail:
          case (Left(error: DispenseError.RefundedError.type), finalState) => finalState
        .map:
          verifyFundsUnchanged(accountId, money, initialState, _, TransactionState.Refunded.some)

  test("should trigger manual intervention when refunding funds to the account fails"):
    forAllF(withdrawalTestCaseGen):
      case (terminalId, accountId, money, initialState, _) =>
        withService(terminalId, initialState): (atmApplicationService, stubs) =>
          stubs.failingToRefund
            *> atmApplicationService
              .withdraw(accountId, money)
              .interceptMessage[SimulatedFailure.type]("Simulated failure")
        .map: (_, finalState) =>
          verifyFundsInconsistency(accountId, money, initialState, finalState)

  test("should return an error when money cannot be dispensed"):
    forAllF(withdrawalTestCaseGen):
      case (terminalId, accountId, money, initialState, _) =>
        withService(terminalId, initialState): (atmApplicationService, stubs) =>
          stubs.failingHardware
            *> atmApplicationService
              .withdraw(accountId, money)
              .interceptMessage[SimulatedFailure.type]("Simulated failure")
        .map: (_, finalState) =>
          verifyCashInconsistency(accountId, money, initialState, finalState)

object AtmApplicationServiceSuite:
  extension (self: Stubs)
    def failingToDispenseCash: IO[Stubs] =
      IO.pure(self)
        .flatTap:
          _.cashDispenserServiceStub
            .setFailureRate(alwaysFailed)
    def failingToDecrementCashInventory: IO[Stubs] =
      IO.pure(self)
        .flatTap:
          _.atmRepositoryStub
            .setFailureRate(alwaysFailed)
    def failingToRefund: IO[Stubs] =
      self.failingToDecrementCashInventory.flatTap:
        _.accountRepositoryStub
          .setFailureRate(alwaysFailed)
    def failingHardware: IO[Stubs] =
      IO.pure(self)
        .flatTap:
          _.physicalDispenserStub
            .setFailureRate(alwaysFailed)
