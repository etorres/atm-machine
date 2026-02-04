package es.eriktorr
package atm.application

import atm.application.AtmApplicationServiceSuite.{testCaseGen, TestCase}
import atm.application.AtmApplicationServiceSuiteRunner.{runWith, AtmApplicationServiceState}
import cash.domain.CashGenerators.{availabilityGen, currencyGen, denominationGen, moneyGen}
import cash.domain.{Denomination, Money, Quantity}

import cats.effect.IO
import cats.implicits.*
import cats.mtl.Handle
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.scalacheck.Gen
import org.scalacheck.cats.implicits.given
import org.scalacheck.effect.PropF.forAllF

final class AtmApplicationServiceSuite extends CatsEffectSuite with ScalaCheckEffectSuite:
  test("should withdraw an amount of money in the given currency"):
    forAllF(testCaseGen):
      case TestCase(money, initialState, expectedFinalState) =>
        runWith(initialState): atmApplicationService =>
          Handle
            .allow[AtmApplicationService.Error]:
              atmApplicationService.withdraw(money)
            .rescue: error =>
              IO.fromEither(error.asLeft)
        .map:
          case (result, obtainedFinalState) =>
            assert(result.isRight)
            assertEquals(obtainedFinalState, expectedFinalState)

object AtmApplicationServiceSuite:
  final private case class TestCase(
      money: Money,
      initialState: AtmApplicationServiceState,
      expectedFinalState: AtmApplicationServiceState,
  )

  private val testCaseGen =
    for
      currency <- currencyGen
      money <- moneyGen(currency)

      denominations <- Gen
        .containerOfN[Set, Denomination](7, denominationGen)
        .map(_.toList)
      availabilities <- denominations.traverse: denomination =>
        availabilityGen.map(denomination -> _)

      availableDenominations = availabilities.filter:
        case (_, availability) => availability > 0
      size <- Gen.choose(0, availableDenominations.length)
      removed <- availableDenominations
        .take(size)
        .traverse:
          case (denomination, availability) =>
            for
              amount <- Gen.choose(1, availability)
              quantity = Quantity.applyUnsafe(amount)
            yield denomination -> quantity

      initialState = AtmApplicationServiceState.empty
        .setInventories(Map((money.amount, availabilities.toMap) -> removed.toMap))
        .setAvailabilities(Map(currency -> availabilities.toMap))
      expectedFinalState = initialState
        .setNotes(List(removed.toMap))
        .setRemoved(List(currency -> removed.toMap))
    yield TestCase(
      money,
      initialState,
      expectedFinalState,
    )
