package es.eriktorr
package atm.infrastructure

import com.fortysevendeg.scalacheck.datetime.YearRange
import com.fortysevendeg.scalacheck.datetime.jdk8.ArbitraryJdk8.arbInstantJdk8
import com.fortysevendeg.scalacheck.datetime.jdk8.granularity.seconds as zonedDateTimeSeconds
import org.scalacheck.Gen

import java.time.*

object TemporalGenerators:
  private val yearRange = YearRange.between(1990, 2060)

  val instantGen: Gen[Instant] =
    arbInstantJdk8(using
      granularity = zonedDateTimeSeconds,
      yearRange = yearRange,
    ).arbitrary
