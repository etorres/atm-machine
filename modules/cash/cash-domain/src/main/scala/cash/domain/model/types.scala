package es.eriktorr
package cash.domain.model

import io.github.iltotore.iron.DescribedAs
import io.github.iltotore.iron.constraint.any.Not
import io.github.iltotore.iron.constraint.string.{Blank, Match}

object types:
  /** IBAN Regex breakdown:
    *
    *   - 2 letter Country Code
    *   - 2 Check Digits
    *   - 4 to 30 alphanumeric characters (Basic Bank Account Number)
    */
  type IbanFormat = Match["^[A-Z]{2}[0-9]{2}[A-Z0-9]{4,30}$"]

  type NonEmptyString =
    DescribedAs[Not[Blank], "Should contain at least one non-whitespace character"]
