package es.eriktorr
package cash

import cash.Cash.{Availability, Denomination, Quantity}

import cats.data.EitherNec
import cats.implicits.{catsSyntaxTuple2Semigroupal, showInterpolator}
import io.github.iltotore.iron.cats.*

object CashExtensions:
  extension (self: (Int, Int))
    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def toCash: (Denomination, Availability) =
      self.toCashEitherNec match
        case Left(errors) =>
          val msg = errors.toNonEmptyList.toList.mkString("[", ",", "]")
          throw IllegalArgumentException(show"Conversion failed with the following errors: $msg")
        case Right(value) => value

    private def toCashEitherNec: EitherNec[String, (Denomination, Availability)] =
      val (denomination, availability) = self
      (
        Denomination.eitherNec(denomination),
        Availability.eitherNec(availability),
      ).tupled

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def toDispensedCash: (Denomination, Quantity) =
      self.toDispensedCashEitherNec match
        case Left(errors) =>
          val msg = errors.toNonEmptyList.toList.mkString("[", ",", "]")
          throw IllegalArgumentException(show"Conversion failed with the following errors: $msg")
        case Right(value) => value

    private def toDispensedCashEitherNec: EitherNec[String, (Denomination, Quantity)] =
      val (denomination, availability) = self
      (
        Denomination.eitherNec(denomination),
        Quantity.eitherNec(availability),
      ).tupled
