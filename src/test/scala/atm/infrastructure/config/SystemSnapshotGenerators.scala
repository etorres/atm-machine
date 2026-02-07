package es.eriktorr
package atm.infrastructure.config

import cash.domain.model.*
import cash.domain.model.CashGenerators.{accountIdGen, availabilityGen, denominationGen}

import cats.implicits.*
import org.scalacheck.Gen
import org.scalacheck.cats.implicits.given
import squants.market.{defaultCurrencySet, Currency}

object SystemSnapshotGenerators:
  private val accountsGen =
    for
      size <- Gen.choose(1, 7)
      accountIds <- Gen
        .containerOfN[Set, AccountId](size, accountIdGen)
        .map(_.toList)
      accounts <- accountIds.traverse: accountId =>
        Gen
          .choose(0, 100_000)
          .flatMap: balance =>
            accountId -> BigDecimal(balance)
    yield accounts.toMap

  private val cashInventoryGen =
    for
      size <- Gen.choose(1, 3)
      currencies <- Gen
        .containerOfN[Set, Currency](size, Gen.oneOf(defaultCurrencySet))
        .map(_.toList)
      cashInventory <- currencies
        .traverse: currency =>
          for
            denominations <- denominationsGen
            inventory <- denominations
              .traverse: denomination =>
                availabilityGen.map(denomination -> _)
              .map(_.toMap)
          yield currency -> inventory
        .map(_.toMap)
    yield cashInventory

  private val denominationsGen =
    for
      size <- Gen.choose(1, 3)
      denominations <- Gen
        .containerOfN[Set, Denomination](size, denominationGen)
        .map(_.toList)
    yield denominations

  def systemSnapshotGen(
      accountsGen: Gen[Map[AccountId, BigDecimal]] = accountsGen,
      cashInventoryGen: Gen[Map[Currency, Map[Denomination, Availability]]] = cashInventoryGen,
  ): Gen[SystemSnapshot] =
    (accountsGen, cashInventoryGen)
      .mapN(SystemSnapshot.apply)
