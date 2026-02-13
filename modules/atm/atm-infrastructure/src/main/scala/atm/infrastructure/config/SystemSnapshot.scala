package es.eriktorr
package atm.infrastructure.config

import atm.domain.model.AccountId
import cash.domain.model.*

import io.circe.*
import io.github.iltotore.iron.circe.given
import squants.market.{defaultMoneyContext, Currency, MoneyContext}

final case class SystemSnapshot(
    accounts: Map[AccountId, BigDecimal],
    cashInventory: Map[Currency, Map[Denomination, Availability]],
) derives Codec

object SystemSnapshot:
  given MoneyContext = defaultMoneyContext

  given Codec[Currency] = Codec.from(
    Decoder.decodeString.emapTry: value =>
      Currency(value),
    Encoder.encodeString.contramap[Currency](_.code),
  )

  given KeyDecoder[Currency] =
    KeyDecoder.instance[Currency]: (key: String) =>
      Currency(key).toEither.toOption

  given KeyEncoder[Currency] =
    KeyEncoder.instance[Currency]: (currency: Currency) =>
      currency.code
