package es.eriktorr
package test.utils

import org.scalacheck.{Arbitrary, Gen}

final class ScalaCheckShuffler(seed: Long):
  private lazy val rng = new java.util.Random(seed)
  private lazy val scalaRng = scala.util.Random.javaRandomToRandom(rng)

  def shuffle[A](list: List[A]): List[A] =
    scalaRng.shuffle(list)

object ScalaCheckShuffler:
  val shufflingGen: Gen[ScalaCheckShuffler] =
    Arbitrary.arbLong.arbitrary
      .map(ScalaCheckShuffler.apply)
