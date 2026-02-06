package es.eriktorr
package atm.infrastructure.persistence.doobie

import atm.domain.model.types.TransactionState
import cash.domain.model.{AccountId, Money}

import cats.implicits.*
import doobie.{Get, Meta, Put, Read, Write}
import squants.market.{defaultMoneyContext, Currency, MoneyContext}

trait DoobieAuditProtocol:
  given Meta[AccountId] =
    Meta[String].imap(AccountId.applyUnsafe)(identity)

  given Meta[Money.Amount] =
    Meta[Int].imap(Money.Amount.applyUnsafe)(identity)

  given MoneyContext = defaultMoneyContext

  given Meta[Currency] =
    Meta[String].tiemap { code =>
      Currency(code).toEither.leftMap(_.getMessage)
    }(_.code)

  given Read[Money] =
    Read[(Money.Amount, Currency)].map: (amount, currency) =>
      Money(amount, currency)

  given Write[Money] =
    Write[(Money.Amount, Currency)].contramap: money =>
      (money.amount, money.currency)

  given Get[TransactionState] =
    Get.deriveEnumString[TransactionState]

  given Put[TransactionState] =
    Put.deriveEnumString[TransactionState]
