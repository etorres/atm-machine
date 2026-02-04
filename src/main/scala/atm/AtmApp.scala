package es.eriktorr
package atm

import atm.application.AtmApplicationService
import atm.config.{AtmConfig, AtmParams}
import atm.domain.{AtmRepository, CashDispenserService}
import atm.infrastructure.{HardwareDispenserAdapter, LinePrinter}
import cash.infrastructure.solvers.OrToolsDenominationSolver

import cats.effect.std.AtomicCell
import cats.effect.{ExitCode, IO}
import cats.implicits.catsSyntaxTuple2Semigroupal
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object AtmApp
    extends CommandIOApp(
      name = "atm-machine",
      header = "ATM Machine",
    ):
  override def main: Opts[IO[ExitCode]] =
    (AtmConfig.opts, AtmParams.opts).mapN:
      case (config, params) =>
        for
          logger <- Slf4jLogger.create[IO]
          given Logger[IO] = logger
          atomicAtmRepository <- AtmRepository
            .make[IO]
            .flatMap(AtomicCell[IO].of)
          denominationSolver = OrToolsDenominationSolver[IO](params.verbose)
          dispenserService = CashDispenserService[IO](denominationSolver)
          linePrinter = LinePrinter[IO]
          physicalDispenser = HardwareDispenserAdapter[IO](linePrinter)
          atmApplicationService = AtmApplicationService[IO](
            atomicAtmRepository,
            dispenserService,
            physicalDispenser,
            config.timeout,
          )
        yield ExitCode.Success
