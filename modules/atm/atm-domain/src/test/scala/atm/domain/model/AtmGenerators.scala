package es.eriktorr
package atm.domain.model

import cats.implicits.showInterpolator
import org.scalacheck.Gen

import java.util.Locale

object AtmGenerators:
  val accountIdGen: Gen[AccountId] =
    for
      countryCode <- Gen.oneOf(countryCodes)
      checkDigits <- Gen.choose(10, 99)
      bankAccountNumber <-
        Gen
          .choose(4, 30)
          .flatMap: size =>
            Gen.stringOfN(
              size,
              Gen.oneOf(Gen.alphaUpperChar, Gen.numChar),
            )
      accountId = show"$countryCode$checkDigits$bankAccountNumber"
    yield AccountId.applyUnsafe(accountId)

  val terminalIdGen: Gen[TerminalId] =
    for
      size <- Gen.choose(3, 12)
      terminalId <- Gen.stringOfN(size, Gen.alphaNumChar)
    yield TerminalId.applyUnsafe(terminalId)

  private lazy val countryCodes = Locale.getISOCountries.toList
