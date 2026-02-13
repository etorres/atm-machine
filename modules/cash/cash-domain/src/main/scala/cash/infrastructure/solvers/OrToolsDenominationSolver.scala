package es.eriktorr
package cash.infrastructure.solvers

import cash.domain.DenominationSolver
import cash.domain.model.{Availability, Denomination, Money, Quantity}

import cats.effect.Async
import cats.implicits.*
import cats.mtl.Raise
import cats.mtl.implicits.given
import com.google.ortools.linearsolver.MPSolver

import scala.util.chaining.scalaUtilChainingOps

final class OrToolsDenominationSolver[F[_]: Async](
    numProcessors: Int,
) extends DenominationSolver[F]:
  override def calculateMinimumNotes(
      targetAmount: Money.Amount,
      inventory: Map[Denomination, Availability],
  )(using Raise[F, DenominationSolver.Error]): F[Map[Denomination, Quantity]] =
    OrToolsSolverFactory
      .make[F](OrToolsSolverFactory.Solver.SCIP, numProcessors)
      .flatMap: solver =>
        solve(
          amount = targetAmount,
          availability = inventory.values.toArray,
          denominations = inventory.keys.toArray,
          solver = solver,
        )

  private def solve(
      amount: Int,
      availability: Array[Int],
      denominations: Array[Int],
      solver: MPSolver,
  )(using Raise[F, DenominationSolver.Error]): F[Map[Denomination, Quantity]] =
    val problemSize = denominations.length
    val (objective, variables) =
      Array
        .tabulate(problemSize): i =>
          solver.makeIntVar(0, availability(i), show"x_$i")
        .tap: variables =>
          val constraint = solver.makeConstraint(amount, amount, "amount")
          (0 until problemSize).foreach: idx =>
            constraint.setCoefficient(variables(idx), denominations(idx))
        .pipe: variables =>
          val objective = solver.objective()
          (0 until problemSize).foreach: idx =>
            objective.setCoefficient(variables(idx), 1)
          objective.setMinimization()
          objective -> variables
    (for
      resultStatus <- Async[F].delay(solver.solve())
      result = resultStatus match
        case MPSolver.ResultStatus.OPTIMAL | MPSolver.ResultStatus.FEASIBLE =>
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
          ifTrue = result.pure[F],
          ifFalse = DenominationSolver.Error.NotSolved.raise[F, Map[Denomination, Quantity]],
        )
  end solve

object OrToolsDenominationSolver:
  def apply[F[_]: Async](
      numProcessors: Int = 1,
  ): OrToolsDenominationSolver[F] =
    new OrToolsDenominationSolver[F](numProcessors)
