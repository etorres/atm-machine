package es.eriktorr
package atm.application

import atm.application.AtmApplicationService.{availableDenominations, DispenseError}
import atm.application.AtmApplicationServiceSuite.*
import atm.application.gen.AtmGenerators
import atm.application.support.AtmTestRunner.Stubs
import atm.application.support.{AtmTestAudit, AtmTestRunner}
import atm.application.types.StubStates
import atm.domain.model.types.TransactionState
import test.utils.FailureRate.alwaysFailed

import cats.effect.IO
import cats.implicits.*
import org.scalacheck.effect.PropF.forAllF

final class AtmApplicationServiceSuite extends AtmTestRunner with AtmGenerators with AtmTestAudit:
  test("should withdraw an amount of money in the given currency"):
    forAllF(withdrawalTestCaseGen):
      case (accountId, money, initialState) =>
        withService: (atmApplicationService, stubs) =>
          stubs.withState(initialState)
            *> atmApplicationService.withdraw(accountId, money)
        .map:
          case (Left(error), _) =>
            fail("Unexpected error", error)
          case (Right(_), finalState) =>
            verifyFundsInvariants((accountId, money, initialState), finalState)

  test("should return an error when the account has insufficient funds"):
    forAllF(insufficientFundsTestCaseGen):
      case (accountId, money, initialState) =>
        withService: (atmApplicationService, stubs) =>
          stubs.withState(initialState)
            *> atmApplicationService.withdraw(accountId, money)
        .map:
          case (Left(error: DispenseError.InsufficientFunds.type), finalState) =>
            verifyFundsUnchanged((accountId, money, initialState), finalState)
          case (Left(error), _) =>
            fail("Unexpected error", error)
          case (Right(_), _) =>
            fail("Unexpected success")

  test("should return an error when the ATM does not have enough money"):
    forAllF(outOfMoneyTestCaseGen):
      case (accountId, money, initialState) =>
        withService: (atmApplicationService, stubs) =>
          stubs
            .withState(initialState)
            .failingToDispenseCash
            *> atmApplicationService.withdraw(accountId, money)
        .map:
          case (Left(DispenseError.InsufficientCash(availableDenominations)), finalState) =>
            val finalDenominations = finalState.atmRepositoryState.value
              .getOrElse(money.currency, Map.empty)
              .availableDenominations
            assert(finalDenominations == availableDenominations, "Available denominations")
            verifyFundsUnchanged((accountId, money, initialState), finalState)
          case (Left(error), _) =>
            fail("Unexpected error", error)
          case (Right(_), _) =>
            fail("Unexpected success")

  test("should refund withdrawn money if cash inventory update fails"):
    forAllF(withdrawalTestCaseGen):
      case (accountId, money, initialState) =>
        withService: (atmApplicationService, stubs) =>
          stubs
            .withState(initialState)
            .failingToDecrementCashInventory
            *> atmApplicationService.withdraw(accountId, money)
        .map:
          case (Left(error), _) =>
            fail("Unexpected error", error)
          case (Right(_), finalState) =>
            verifyFundsUnchanged(
              (accountId, money, initialState),
              finalState,
              TransactionState.Refunded.some,
            )

  test("should trigger manual intervention when refunding funds to the account fails"):
    forAllF(withdrawalTestCaseGen):
      case (accountId, money, initialState) =>
        withService: (atmApplicationService, stubs) =>
          stubs
            .withState(initialState)
            .failingToRefund
            *> atmApplicationService.withdraw(accountId, money)
        .map:
          case (Left(error), _) =>
            fail("Unexpected error", error)
          case (Right(_), finalState) =>
            verifyFundsInconsistency(
              (accountId, money, initialState),
              finalState,
            )

  test("should return an error when money cannot be dispensed"):
    forAllF(withdrawalTestCaseGen):
      case (accountId, money, initialState) =>
        withService: (atmApplicationService, stubs) =>
          stubs
            .withState(initialState)
            .failingHardware
            *> atmApplicationService.withdraw(accountId, money)
        .map:
          case (Left(error: RuntimeException), finalState) =>
            verifyCashInconsistency((accountId, money, initialState), finalState)
          case (Left(error), _) =>
            fail("Unexpected error", error)
          case (Right(_), _) =>
            fail("Unexpected success")

object AtmApplicationServiceSuite:
  extension (self: Stubs)
    def withState(
        stubStates: StubStates,
    ): IO[Stubs] =
      for
        _ <- self.accountRepositoryStub
          .setState(stubStates.accountRepositoryState)
        _ <- self.atmRepositoryStub
          .setState(stubStates.atmRepositoryState)
        _ <- self.cashDispenserServiceStub
          .setState(stubStates.cashDispenserServiceState)
      yield self

  extension (self: IO[Stubs])
    def failingToDispenseCash: IO[Stubs] =
      self.flatTap(
        _.cashDispenserServiceStub
          .setFailureRate(alwaysFailed),
      )
    def failingToDecrementCashInventory: IO[Stubs] =
      self.flatTap(
        _.atmRepositoryStub
          .setFailureRate(alwaysFailed),
      )
    def failingToRefund: IO[Stubs] =
      self.failingToDecrementCashInventory.flatTap:
        _.accountRepositoryStub
          .setFailureRate(alwaysFailed)
    def failingHardware: IO[Stubs] =
      self.flatTap(
        _.physicalDispenserStub
          .setFailureRate(alwaysFailed),
      )
