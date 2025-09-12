package es.eriktorr
package atm

import atm.Atm.TellingError
import atm.AtmSuite.{testCaseGen, TestCase}
import atm.AtmSuiteRunner.{runWith, AtmSuiteState}
import cash.Cash
import cash.Cash.{Denomination, Quantity}
import cash.CashGenerators.{availabilityGen, cashGen, currencyGen, denominationGen}

import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, toTraverseOps}
import cats.mtl.Handle
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.scalacheck.Gen
import org.scalacheck.cats.implicits.genInstances
import org.scalacheck.effect.PropF.forAllF

final class AtmSuite extends CatsEffectSuite with ScalaCheckEffectSuite:
  test("should withdraw an amount of money in the given currency"):
    forAllF(testCaseGen):
      case TestCase(cash, initialState, expectedFinalState) =>
        runWith(initialState): atm =>
          Handle
            .allow[TellingError]:
              atm.withdraw(cash).map(_.asRight[Unit])
            .rescue: error =>
              IO.fromEither(error.asLeft)
        .map:
          case (result, obtainedFinalState) =>
            assert(result.isRight)
            assertEquals(obtainedFinalState, expectedFinalState)

object AtmSuite:
  final private case class TestCase(
      cash: Cash,
      initialState: AtmSuiteState,
      expectedFinalState: AtmSuiteState,
  )

  private val testCaseGen =
    for
      currency <- currencyGen
      cash <- cashGen(currency)

      denominations <- Gen.containerOfN[Set, Denomination](7, denominationGen).map(_.toList)
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

      lines = removed
        .sortBy:
          case (denomination, _) => denomination
        .map(Atm.toLine)

      initialState = AtmSuiteState.empty
        .setAmounts(Map((cash.amount, availabilities.toMap) -> removed.toMap))
        .setAvailabilities(Map(currency -> availabilities.toMap))
      expectedFinalState = initialState
        .setLines(lines)
        .setRemoved(List(currency -> removed.toMap))
    yield TestCase(cash, initialState, expectedFinalState)
