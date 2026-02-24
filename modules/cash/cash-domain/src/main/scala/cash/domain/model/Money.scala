package es.eriktorr
package cash.domain.model

import cash.domain.model.Money.given

import cats.Show
import cats.derived.*
import io.github.iltotore.iron.RefinedSubtype
import io.github.iltotore.iron.constraint.numeric.Positive
import squants.market.Currency

final case class Money(
    amount: Money.Amount,
    currency: Currency,
) derives Show

object Money:
  type Amount = Amount.T

  object Amount extends RefinedSubtype[Int, Positive]

  given Show[Currency] with
    override def show(currency: Currency): String =
      currency.code
