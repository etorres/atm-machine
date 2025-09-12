package es.eriktorr.atm
package application

import com.monovore.decline.Opts

final case class AtmParams(verbose: Boolean)

object AtmParams:
  def opts: Opts[AtmParams] = Opts
    .flag("verbose", short = "v", help = "Print extra metadata to the logs.")
    .orFalse
    .map(AtmParams.apply)
