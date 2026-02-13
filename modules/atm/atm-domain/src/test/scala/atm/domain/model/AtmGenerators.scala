package es.eriktorr
package atm.domain.model

import org.scalacheck.Gen

object AtmGenerators:
  val accountIdGen: Gen[AccountId] =
    for
      size <- Gen.choose(3, 12)
      accountId <- Gen.stringOfN(size, Gen.alphaNumChar)
    yield AccountId.applyUnsafe(accountId)
