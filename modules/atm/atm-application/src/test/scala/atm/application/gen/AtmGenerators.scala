package es.eriktorr
package atm.application.gen

import atm.application.AtmApplicationService
import atm.application.types.{emptyStubStates, TestCase}
import atm.domain.model.AtmGenerators.{accountIdGen, terminalIdGen}
import atm.domain.model.Receipt
import atm.domain.model.types.AuditEntryGenerators.transactionIdGen
import cash.domain.model.*
import cash.domain.model.CashGenerators.{currencyGen, moneyGen}
import test.gen.TemporalGenerators.{instantGen, withinInstantRange}
import test.utils.ScalaCheckShuffler
import test.utils.ScalaCheckShuffler.shufflingGen

import cats.collections.Range
import cats.implicits.*
import org.scalacheck.Gen
import org.scalacheck.cats.implicits.*

import java.time.Instant
import java.util.UUID

trait AtmGenerators:
  private def cashInventoryGen(using
      shuffler: ScalaCheckShuffler,
  ) =
    for
      possibleDenominations = List(1, 2, 5, 10, 20, 100, 200, 500)
        .map(Denomination.applyUnsafe)
      availableDenominations <- Gen
        .choose(1, possibleDenominations.size)
        .map(shuffler.shuffle(possibleDenominations).take)
      unavailableDenominations =
        (possibleDenominations.toSet -- availableDenominations).toList
      availabilities <- possibleDenominations.traverse: denomination =>
        Gen
          .const(availableDenominations.contains(denomination))
          .ifM(
            ifTrue = Gen.choose(1, 1000),
            ifFalse = 0,
          )
          .map: availability =>
            denomination -> Availability.applyUnsafe(availability)
    yield availabilities.toMap

  private def withdrawalGen(
      cashInventory: Map[Denomination, Availability],
  )(using
      shuffler: ScalaCheckShuffler,
  ) =
    for
      availableDenominations = cashInventory.toList.filter:
        case (_, availability) => availability > 0
      selectedDenominations <-
        Gen
          .choose(1, availableDenominations.length)
          .map(shuffler.shuffle(availableDenominations).take)
      withdrawal <- selectedDenominations
        .traverse:
          case (denomination, availability) =>
            Gen
              .choose(1, availability)
              .map: quantity =>
                denomination -> Quantity.applyUnsafe(quantity)
        .map(_.toMap)
    yield withdrawal

  private def withdrawalMoneyGen(
      withdrawal: Map[Denomination, Quantity],
  ) =
    for
      amount = Money.Amount.applyUnsafe(
        withdrawal.map(_ * _).sum,
      )
      currency <- currencyGen
      money = Money(amount, currency)
    yield money

  val withdrawalTestCaseGen: Gen[TestCase] =
    for
      shuffler <- shufflingGen
      given ScalaCheckShuffler = shuffler

      terminalId <- terminalIdGen

      transactionId <- transactionIdGen
      completeInstant <- instantGen
      otherInstants <- Gen.listOfN(
        3,
        withinInstantRange(
          Range(completeInstant.minusSeconds(60), completeInstant.minusSeconds(1)),
        ),
      )
      receiptInstant = otherInstants.maxOption.getOrElse(Instant.MIN)

      cashInventory <- cashInventoryGen
      withdrawal <- withdrawalGen(cashInventory)
      money <- withdrawalMoneyGen(withdrawal)

      accountId <- accountIdGen
      balance <- Gen.choose(money.amount, money.amount + 10_000)

      initialState = emptyStubStates
        .setAccounts(Map(accountId -> balance))
        .setCashInventory(Map(money.currency -> cashInventory))
        .setInstants((completeInstant :: otherInstants).sorted)
        .setWithdrawableFunds(Map((money.amount, cashInventory) -> withdrawal))
        .setUUIDs(List(transactionId).map(UUID.fromString))

      expectedReceipt = Receipt(
        transactionId,
        terminalId,
        accountId,
        money,
        AtmApplicationService.timestampFrom(receiptInstant),
        Receipt.OperationType.Withdrawal,
      )
    yield (
      terminalId,
      accountId,
      money,
      initialState,
      Some(expectedReceipt),
    )

  val insufficientFundsTestCaseGen: Gen[TestCase] =
    for
      shuffler <- shufflingGen
      given ScalaCheckShuffler = shuffler

      terminalId <- terminalIdGen

      cashInventory <- cashInventoryGen
      withdrawal <- withdrawalGen(cashInventory)
      money <- withdrawalMoneyGen(withdrawal)

      accountId <- accountIdGen
      balance <- Gen.choose(0, math.max(0, money.amount - 1))

      initialState = emptyStubStates
        .setAccounts(Map(accountId -> balance))
        .setCashInventory(Map(money.currency -> cashInventory))
        .setWithdrawableFunds(Map((money.amount, cashInventory) -> withdrawal))
    yield (
      terminalId,
      accountId,
      money,
      initialState,
      None,
    )

  val outOfMoneyTestCaseGen: Gen[TestCase] =
    for
      shuffler <- shufflingGen
      given ScalaCheckShuffler = shuffler

      terminalId <- terminalIdGen

      cashInventory <- cashInventoryGen
      money <- moneyGen()

      accountId <- accountIdGen
      balance <- Gen.choose(money.amount, money.amount + 10_000)

      initialState = emptyStubStates
        .setAccounts(Map(accountId -> balance))
        .setCashInventory(Map(money.currency -> cashInventory))
    yield (
      terminalId,
      accountId,
      money,
      initialState,
      None,
    )
