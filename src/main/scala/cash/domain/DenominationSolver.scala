package es.eriktorr
package cash.domain

import cash.domain.model.{Availability, Denomination, Money, Quantity}

import cats.mtl.Raise

import scala.util.control.NoStackTrace

trait DenominationSolver[F[_]]:
  def calculateMinimumNotes(
      targetAmount: Money.Amount,
      inventory: Map[Denomination, Availability],
  )(using Raise[F, DenominationSolver.Error]): F[Map[Denomination, Quantity]]

object DenominationSolver:
  enum Error(
      val message: String,
  ) extends RuntimeException(message)
      with NoStackTrace:
    case NotSolved
        extends Error(
          "No solution found within the time limit",
        )
