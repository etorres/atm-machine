package es.eriktorr
package cash.domain.model

import cats.implicits.*
import org.scalacheck.Gen
import org.scalacheck.cats.implicits.given
import squants.market.{defaultCurrencySet, Currency}

object CashGenerators:
  val accountIdGen: Gen[AccountId] =
    for
      size <- Gen.choose(3, 12)
      accountId <- Gen.stringOfN(size, Gen.alphaNumChar)
    yield AccountId.applyUnsafe(accountId)

  private val amountGen =
    Gen.choose(1, 1000).map(Money.Amount.applyUnsafe)

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
