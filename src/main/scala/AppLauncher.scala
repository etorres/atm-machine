package es.eriktorr

import atm.application.AtmApplicationService
import atm.config.{AtmConfig, AtmParams}
import atm.domain.{AccountRepository, AtmRepository, CashDispenserService}
import atm.infrastructure.persistence.doobie.DoobieTransactionAuditor
import atm.infrastructure.ui.ConsoleAtm
import atm.infrastructure.{HardwareDispenserAdapter, LinePrinter}
import cash.infrastructure.solvers.{OrToolsDenominationSolver, OrToolsSolverFactory}

import cats.effect.std.{AtomicCell, Console}
import cats.effect.{ExitCode, IO, Resource}
import cats.implicits.*
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import squants.market.EUR

import scala.concurrent.duration.DurationInt

object AppLauncher
    extends CommandIOApp(
      name = "atm-machine",
      header = "ATM Machine",
    ):
  override def main: Opts[IO[ExitCode]] =
    (AtmConfig.opts, AtmParams.opts).mapN:
      case (config, params) =>
        Resource
          .eval:
            IO.delay:
              Runtime.getRuntime.availableProcessors()
          .flatMap: availableCores =>
            DoobieTransactionAuditor
              .make[IO](availableCores)
              .map(availableCores -> _)
          .use: (availableCores, transactionAuditor) =>
            for
              logger <- Slf4jLogger.create[IO]
              given Logger[IO] = logger
              _ <- warmUpOrTools(params.verbose)
              atomicRepository <- (
                AccountRepository.make[IO],
                AtmRepository.make[IO],
              ).tupled.flatMap(AtomicCell[IO].of)
              denominationSolver = OrToolsDenominationSolver[IO](availableCores)
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
              ui = ConsoleAtm[IO](atmApplicationService, EUR)
              _ <- ui.start()
            yield ExitCode.Success

  private def warmUpOrTools(verbose: Boolean)(using Logger[IO]) =
    for
      _ <- Console[IO].print("Initializing ATM engines (loading OR-Tools)")
      _ <- IO.race(showProgressDots, OrToolsSolverFactory.init[IO](verbose))
      _ <- Console[IO].println(" Done!")
      _ <- Console[IO].println("System Ready.")
    yield ()

  private def showProgressDots =
    (Console[IO].print(".") >> IO.sleep(500.millis)).foreverM
