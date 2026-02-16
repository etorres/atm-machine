package es.eriktorr
package atm.domain.model

import cash.domain.model.types.IbanFormat

import cats.implicits.showInterpolator
import io.github.iltotore.iron.RefinedSubtype

type AccountId = AccountId.T

object AccountId extends RefinedSubtype[String, IbanFormat]:
  extension (self: AccountId)
    def masked: String =
      if self.length <= 8 then "****"
      else show"${self.take(4)}****${self.takeRight(4)}"
