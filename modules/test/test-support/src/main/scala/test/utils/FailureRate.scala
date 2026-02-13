package es.eriktorr
package test.utils

import io.github.iltotore.iron.RefinedSubtype
import io.github.iltotore.iron.constraint.numeric.{GreaterEqual, LessEqual}

type FailureRate = FailureRate.T

object FailureRate extends RefinedSubtype[Double, GreaterEqual[0d] & LessEqual[1d]]:
  val alwaysSucceed: FailureRate = FailureRate.applyUnsafe(0d)
  val alwaysFailed: FailureRate = FailureRate.applyUnsafe(1d)
