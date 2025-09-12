package es.eriktorr
package cash

import cash.Cash.{Availability, Denomination, Quantity}

import org.scalacheck.Gen
import squants.market.{defaultCurrencySet, Currency}

object CashGenerators:
  val availabilityGen: Gen[Availability] = Gen.choose(0, 1000).map(Availability.applyUnsafe)

  val currencyGen: Gen[Currency] = Gen.oneOf(defaultCurrencySet)

  val denominationGen: Gen[Denomination] = Gen.choose(1, 1000).map(Denomination.applyUnsafe)

  val quantityGen: Gen[Quantity] = Gen.choose(1, 1000).map(Quantity.applyUnsafe)

  def cashGen(currencyGen: Gen[Currency] = currencyGen): Gen[Cash] =
    for
      amount <- quantityGen
      currency <- currencyGen
    yield Cash(amount, currency)
