package es.eriktorr.atm
package application

import com.monovore.decline.Opts

import scala.concurrent.duration.{Duration, DurationInt}

final case class AtmConfig(
    timeout: Duration,
)

object AtmConfig:
  def opts: Opts[AtmConfig] =
    Opts
      .env[Duration](
        name = "ATM_RESPONSE_TIMEOUT",
        help = "Set ATM response timeout.",
      )
      .validate("Maximum 1 minute")(_ <= 1.minute)
      .withDefault(2.seconds)
      .map(AtmConfig.apply)
