package es.eriktorr
package cash.domain

import io.github.iltotore.iron.RefinedSubtype
import io.github.iltotore.iron.constraint.numeric.Positive

type Denomination = Denomination.T

object Denomination extends RefinedSubtype[Int, Positive]
