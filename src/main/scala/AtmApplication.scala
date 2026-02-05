package es.eriktorr

import atm.application.AtmApplicationService
import atm.config.{AtmConfig, AtmParams}
import atm.domain.{AccountRepository, AtmRepository, CashDispenserService}
import atm.infrastructure.{HardwareDispenserAdapter, LinePrinter}
import cash.infrastructure.solvers.{OrToolsDenominationSolver, OrToolsSolverFactory}

import cats.effect.std.AtomicCell
import cats.effect.{ExitCode, IO}
import cats.implicits.catsSyntaxTuple2Semigroupal
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import es.eriktorr.atm.repository.TransactionAuditor
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object AtmApplication
    extends CommandIOApp(
      name = "atm-machine",
      header = "ATM Machine",
    ):
  override def main: Opts[IO[ExitCode]] =
    (AtmConfig.opts, AtmParams.opts).mapN:
      case (config, params) =>
        TransactionAuditor
          .make[IO]
          .use: transactionAuditor =>
            for
              logger <- Slf4jLogger.create[IO]
              given Logger[IO] = logger
              _ <- OrToolsSolverFactory.init[IO](params.verbose)
              atomicRepository <- (
                AccountRepository.make[IO],
                AtmRepository.make[IO],
              ).tupled.flatMap(AtomicCell[IO].of)
              denominationSolver = OrToolsDenominationSolver[IO]
              dispenserService = CashDispenserService[IO](denominationSolver)
              linePrinter = LinePrinter[IO]
              physicalDispenser = HardwareDispenserAdapter[IO](linePrinter)
              atmApplicationService = AtmApplicationService[IO](
                atomicRepository,
                transactionAuditor,
                dispenserService,
                physicalDispenser,
                config.timeout,
              )
            yield ExitCode.Success
