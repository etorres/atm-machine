package es.eriktorr
package cash

import cash.Cash.Quantity

import io.github.iltotore.iron.RefinedSubtype
import io.github.iltotore.iron.constraint.numeric.{Positive, Positive0}
import squants.market.Currency

final case class Cash(amount: Quantity, currency: Currency)

object Cash:
  type Availability = Availability.T

  object Availability extends RefinedSubtype[Int, Positive0]

  type Denomination = Denomination.T

  object Denomination extends RefinedSubtype[Int, Positive]

  type Quantity = Quantity.T

  object Quantity extends RefinedSubtype[Int, Positive]
