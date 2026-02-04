package es.eriktorr
package cash.domain

import io.github.iltotore.iron.RefinedSubtype
import io.github.iltotore.iron.constraint.numeric.Positive
import squants.market.Currency

final case class Money(
    amount: Money.Amount,
    currency: Currency,
)

object Money:
  type Amount = Amount.T

  object Amount extends RefinedSubtype[Int, Positive]
