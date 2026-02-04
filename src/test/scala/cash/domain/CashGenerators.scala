package es.eriktorr
package cash.domain

import cash.domain.Money.Amount

import cats.implicits.*
import org.scalacheck.Gen
import org.scalacheck.cats.implicits.given
import squants.market.{defaultCurrencySet, Currency}

object CashGenerators:
  private val amountGen =
    Gen.choose(1, 1000).map(Amount.applyUnsafe)

  val availabilityGen: Gen[Availability] =
    Gen.choose(0, 1000).map(Availability.applyUnsafe)

  val currencyGen: Gen[Currency] =
    Gen.oneOf(defaultCurrencySet)

  val denominationGen: Gen[Denomination] =
    Gen.choose(1, 1000).map(Denomination.applyUnsafe)

  val quantityGen: Gen[Quantity] =
    Gen.choose(1, 1000).map(Quantity.applyUnsafe)

  def moneyGen(
      currencyGen: Gen[Currency] = currencyGen,
  ): Gen[Money] =
    (amountGen, currencyGen)
      .mapN(Money.apply)
