package es.eriktorr
package test.gen

import cats.collections.Range
import com.fortysevendeg.scalacheck.datetime.GenDateTime.genDateTimeWithinRange
import com.fortysevendeg.scalacheck.datetime.instances.jdk8.jdk8Instant
import com.fortysevendeg.scalacheck.datetime.jdk8.ArbitraryJdk8.arbInstantJdk8
import com.fortysevendeg.scalacheck.datetime.jdk8.granularity.seconds as zonedDateTimeSeconds
import com.fortysevendeg.scalacheck.datetime.{Granularity, YearRange}
import org.scalacheck.Gen

import java.time.*
import java.time.temporal.ChronoUnit.SECONDS

object TemporalGenerators:
  private val yearRange = YearRange.between(1990, 2060)

  private val instantSeconds: Granularity[Instant] =
    new Granularity[Instant]:
      val normalize: Instant => Instant =
        (instant: Instant) => instant.truncatedTo(SECONDS)
      val description: String = "Seconds"

  val instantGen: Gen[Instant] =
    arbInstantJdk8(using
      granularity = zonedDateTimeSeconds,
      yearRange = yearRange,
    ).arbitrary

  def withinInstantRange(instantRange: Range[Instant]): Gen[Instant] =
    genDateTimeWithinRange(
      instantRange.start,
      Duration.ofSeconds(SECONDS.between(instantRange.start, instantRange.end)),
    )(using scDateTime = jdk8Instant, granularity = instantSeconds)
