package es.eriktorr
package cash

import cash.Cash.{Availability, Denomination, Quantity}
import cash.CashDispenser.DispensingError
import cash.CashDispenserSuite.testCases
import cash.CashExtensions.{toCash, toDispensedCash}

import cats.effect.{IO, Resource}
import cats.implicits.{catsSyntaxEitherId, showInterpolator, toTraverseOps}
import cats.mtl.Handle
import munit.CatsEffectSuite
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

final class CashDispenserSuite extends CatsEffectSuite:
  test("should find the minimum number of notes needed to make up an amount"):
    IO.delay(cashDispenserFixture())
      .flatMap: cashDispenser =>
        testCases.traverse:
          case (testCase, idx) =>
            Handle
              .allow[DispensingError]:
                cashDispenser
                  .minimumUnits(
                    testCase.amount,
                    testCase.availabilities,
                  )
                  .map(_.asRight[DispensingError])
              .rescue: error =>
                IO.fromEither(error.asLeft)
              .assertEquals(testCase.expected, show"Test case $idx")

  override def munitFixtures = List(cashDispenserFixture)

  private lazy val cashDispenserFixture =
    ResourceSuiteLocalFixture(
      "cash-dispenser", {
        given Logger[IO] = NoOpLogger.impl[IO]
        Resource.pure(CashDispenser.impl[IO](verbose = false))
      },
    )

object CashDispenserSuite:
  final private case class TestCase(
      availabilities: Map[Denomination, Availability],
      expected: Either[DispensingError, Map[Denomination, Quantity]],
      amount: Quantity,
  )

  private val testCases =
    List(
      TestCase(
        availabilities = Map(
          500 -> 1000,
          200 -> 1000,
          100 -> 1000,
          50 -> 1000,
          20 -> 1000,
          10 -> 1000,
          5 -> 1000,
          2 -> 1000,
          1 -> 1000,
        ).map(toCash),
        expected = Map(
          200 -> 2,
          20 -> 1,
          10 -> 1,
          2 -> 2,
        ).map(toDispensedCash).asRight,
        amount = Quantity.applyUnsafe(434),
      ),
      TestCase(
        availabilities = Map(
          500 -> 2,
          200 -> 3,
          100 -> 5,
          50 -> 12,
          20 -> 20,
          10 -> 50,
          5 -> 100,
          2 -> 250,
          1 -> 500,
        ).map(toCash),
        expected = Map(
          500 -> 2,
          200 -> 3,
          100 -> 1,
          20 -> 1,
          5 -> 1,
        ).map(toDispensedCash).asRight,
        amount = Quantity.applyUnsafe(1725),
      ),
      TestCase(
        availabilities = Map(
          500 -> 0,
          200 -> 0,
          100 -> 4,
          50 -> 12,
          20 -> 19,
          10 -> 50,
          5 -> 99,
          2 -> 250,
          1 -> 500,
        ).map(toCash),
        expected = Map(
          100 -> 4,
          50 -> 12,
          20 -> 19,
          10 -> 44,
          5 -> 1,
        ).map(toDispensedCash).asRight,
        amount = Quantity.applyUnsafe(1825),
      ),
    ).zipWithIndex
