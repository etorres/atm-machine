package es.eriktorr
package cash.domain.model

import io.github.iltotore.iron.DescribedAs
import io.github.iltotore.iron.constraint.any.Not
import io.github.iltotore.iron.constraint.string.Blank

object types:
  type NonEmptyString =
    DescribedAs[Not[Blank], "Should contain at least one non-whitespace character"]
