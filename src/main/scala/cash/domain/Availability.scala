package es.eriktorr
package cash.domain

import io.github.iltotore.iron.RefinedSubtype
import io.github.iltotore.iron.constraint.numeric.Positive0

type Availability = Availability.T

object Availability extends RefinedSubtype[Int, Positive0]:
  val Zero: Availability = Availability.applyUnsafe(0)
