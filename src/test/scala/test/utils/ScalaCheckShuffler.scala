package es.eriktorr
package test.utils

import org.scalacheck.{Arbitrary, Gen}

final class ScalaCheckShuffler(seed: Long):
  def shuffle[A](list: List[A]): List[A] =
    val rng = new java.util.Random(seed)
    scala.util.Random.javaRandomToRandom(rng).shuffle(list)

object ScalaCheckShuffler:
  val shufflingGen: Gen[ScalaCheckShuffler] =
    Arbitrary.arbLong.arbitrary
      .map(ScalaCheckShuffler.apply)
