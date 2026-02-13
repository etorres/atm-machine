package es.eriktorr
package cash.infrastructure.solvers

import cash.domain.DenominationSolver
import cash.domain.model.{Availability, Denomination, Money, Quantity}
import cash.infrastructure.solvers.CashExtensions.{toCash, toDispensedCash}
import cash.infrastructure.solvers.OrToolsDenominationSolverSuite.testCases

import cats.effect.{IO, Resource}
import cats.implicits.*
import cats.mtl.Handle
import munit.{AnyFixture, CatsEffectSuite}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

import scala.concurrent.duration.{Duration, DurationInt}

final class OrToolsDenominationSolverSuite extends CatsEffectSuite:
  test("should find the minimum number of notes needed to make up an amount"):
    IO.delay(denominationSolverFixture())
      .flatMap: denominationSolver =>
        testCases.traverse:
          case (testCase, idx) =>
            Handle
              .allow[DenominationSolver.Error]:
                denominationSolver
                  .calculateMinimumNotes(
                    testCase.amount,
                    testCase.inventory,
                  )
                  .map(_.asRight[DenominationSolver.Error])
              .rescue: error =>
                IO.fromEither(error.asLeft)
              .assertEquals(testCase.expected, show"Test case $idx")

  override def munitFixtures: Seq[AnyFixture[?]] = List(denominationSolverFixture)

  override def munitIOTimeout: Duration = 40.seconds

  private lazy val denominationSolverFixture =
    ResourceSuiteLocalFixture(
      "DenominationSolver", {
        given Logger[IO] = NoOpLogger.impl[IO]
        Resource.make(
          OrToolsSolverFactory
            .init[IO](verbose = false)
            .as(OrToolsDenominationSolver[IO]()),
        )(_ => IO.unit)
      },
    )

object OrToolsDenominationSolverSuite:
  final private case class TestCase(
      inventory: Map[Denomination, Availability],
      expected: Either[DenominationSolver.Error, Map[Denomination, Quantity]],
      amount: Money.Amount,
  )

  private val testCases =
    List(
      TestCase(
        inventory = Map(
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
        amount = Money.Amount.applyUnsafe(434),
      ),
      TestCase(
        inventory = Map(
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
        amount = Money.Amount.applyUnsafe(1725),
      ),
      TestCase(
        inventory = Map(
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
        amount = Money.Amount.applyUnsafe(1825),
      ),
    ).zipWithIndex
