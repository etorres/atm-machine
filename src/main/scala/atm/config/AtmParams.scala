package es.eriktorr
package atm.config

import cats.implicits.*
import com.monovore.decline.Opts

import java.nio.file.Path

final case class AtmParams(
    snapshotPath: Path,
    verbose: Boolean,
)

object AtmParams:
  def opts: Opts[AtmParams] =
    (
      Opts.argument[Path](
        metavar = "snapshot-file",
      ),
      Opts
        .flag(
          long = "verbose",
          short = "v",
          help = "Print extra metadata to the logs",
        )
        .orFalse,
    ).mapN(AtmParams.apply)
