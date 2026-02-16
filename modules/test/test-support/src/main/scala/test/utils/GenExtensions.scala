package es.eriktorr
package test.utils

import cats.implicits.showInterpolator
import org.scalacheck.Gen
import org.scalacheck.Gen.Parameters
import org.scalacheck.rng.Seed

object GenExtensions:
  extension [T](self: Gen[T])
    def sampleWithSeed(
        seed: Option[Seed] = None,
        verbose: Boolean = true,
    ): T =
      val sampleSeed = seed.getOrElse(Seed.random())
      if verbose then println(show"Sampling with: ${sampleSeed.toString}")
      self.pureApply(Parameters.default.withNoInitialSeed, sampleSeed)
