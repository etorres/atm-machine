package es.eriktorr
package cash

import cash.Cash.{Availability, Denomination, Quantity}
import cash.CashDispenser.DispensingError
import commons.OptimizationUtils
import commons.OptimizationUtils.Solver

import cats.effect.Async
import cats.implicits.*
import cats.mtl.Raise
import cats.mtl.implicits.given
import com.google.ortools.linearsolver.MPSolver
import org.typelevel.log4cats.Logger

import scala.util.chaining.scalaUtilChainingOps
import scala.util.control.NoStackTrace

trait CashDispenser[F[_]]:
  def minimumUnits(
      amount: Quantity,
      availabilities: Map[Denomination, Availability],
  )(using Raise[F, DispensingError]): F[Map[Denomination, Quantity]]

object CashDispenser:
  def impl[F[_]: {Async, Logger}](verbose: Boolean): CashDispenser[F] =
    new CashDispenser[F]:
      override def minimumUnits(
          amount: Quantity,
          availabilities: Map[Denomination, Availability],
      )(using Raise[F, DispensingError]): F[Map[Denomination, Quantity]] =
        def solve(
            amount: Int,
            availability: Array[Int],
            denominations: Array[Int],
            solver: MPSolver,
        )(using Raise[F, DispensingError]): F[Map[Denomination, Quantity]] =
          val problemSize = denominations.length
          val (objective, variables) = Array
            .tabulate(problemSize) { i =>
              solver.makeIntVar(0, availability(i), show"x_$i")
            }
            .tap { variables =>
              val constraint = solver.makeConstraint(amount, amount, "amount")
              (0 until problemSize).foreach: idx =>
                constraint.setCoefficient(variables(idx), denominations(idx))
            }
            .pipe { variables =>
              val objective = solver.objective()
              (0 until problemSize).foreach: idx =>
                objective.setCoefficient(variables(idx), 1)
              objective.setMinimization()
              objective -> variables
            }
          (for
            resultStatus <- Async[F].delay(solver.solve())
            result = resultStatus match
              case MPSolver.ResultStatus.OPTIMAL =>
                variables
                  .filter(_.solutionValue() > 0)
                  .ensuring(
                    _.length <= objective.value(),
                    "The objective does not match the results",
                  )
                  .map: variable =>
                    val denomination = Denomination.applyUnsafe(
                      denominations(variable.index()),
                    )
                    val quantity = Quantity.applyUnsafe(
                      variable.solutionValue().toInt,
                    )
                    denomination -> quantity
                  .toMap
              case _ => Map.empty
          yield result).flatMap: result =>
            Async[F]
              .pure(result.nonEmpty)
              .ifM(
                ifTrue = Async[F].pure(result),
                ifFalse = DispensingError.NotSolved.raise[F, Map[Denomination, Quantity]],
              )

        for
          solver <- OptimizationUtils.create[F](Solver.SCIP, verbose)
          result <- solve(
            amount = amount,
            availability = availabilities.values.toArray,
            denominations = availabilities.keys.toArray,
            solver = solver,
          )
        yield result

  enum DispensingError(val message: String) extends RuntimeException(message) with NoStackTrace:
    case NotSolved extends DispensingError("The problem does not have an optimal solution")
