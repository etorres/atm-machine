package es.eriktorr
package cash.domain.model

import io.github.iltotore.iron.RefinedSubtype
import io.github.iltotore.iron.constraint.numeric.Positive

type Quantity = Quantity.T

object Quantity extends RefinedSubtype[Int, Positive]
