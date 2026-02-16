package es.eriktorr
package atm.config

import atm.domain.model.TerminalId

import cats.implicits.*
import com.monovore.decline.Opts
import io.github.iltotore.iron.decline.given

import java.nio.file.Path

final case class AtmParams(
    snapshotPath: Path,
    terminalId: TerminalId,
    verbose: Boolean,
)

object AtmParams:
  def opts: Opts[AtmParams] =
    (
      Opts.argument[Path](
        metavar = "snapshot-file",
      ),
      Opts.option[TerminalId](
        long = "terminalId",
        help = "Unique identifier for the ATM terminal",
      ),
      Opts
        .flag(
          long = "verbose",
          short = "v",
          help = "Print extra metadata to the logs",
        )
        .orFalse,
    ).mapN(AtmParams.apply)
