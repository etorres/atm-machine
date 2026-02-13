package es.eriktorr
package test.utils

import org.scalacheck.{Arbitrary, Gen}

final class FailureRateSampler(seed: Long):
  private lazy val rng = new java.util.Random(seed)
  private lazy val scalaRng = scala.util.Random.javaRandomToRandom(rng)

  def next(failureRate: FailureRate): FailureRate =
    val roll =
      if failureRate > FailureRate.alwaysSucceed then
        scalaRng.between(FailureRate.alwaysSucceed, failureRate)
      else FailureRate.alwaysSucceed
    FailureRate.applyUnsafe(roll)

object FailureRateSampler:
  val failureRateSamplerGen: Gen[FailureRateSampler] =
    Arbitrary.arbLong.arbitrary
      .map(FailureRateSampler.apply)
