package es.eriktorr
package atm

import atm.application.{AtmConfig, AtmParams}
import cash.CashRepository.InMemory.CashRepositoryState
import cash.{CashDispenser, CashPrinter, CashRepository}

import cats.effect.std.AtomicCell
import cats.effect.{ExitCode, IO, Ref}
import cats.implicits.catsSyntaxTuple2Semigroupal
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object AtmApp extends CommandIOApp(name = "atm-machine", header = "ATM Machine"):
  override def main: Opts[IO[ExitCode]] =
    (AtmConfig.opts, AtmParams.opts).mapN:
      case (config, params) => program(config, params)

  private def program(config: AtmConfig, params: AtmParams) =
    for
      logger <- Slf4jLogger.create[IO]
      given Logger[IO] = logger
      cashDispenser = CashDispenser.impl[IO](params.verbose)
      cashPrinter = CashPrinter.impl[IO]
      cashRepositoryStateRef <- Ref.of[IO, CashRepositoryState](CashRepositoryState.empty)
      atomicCashRepository <-
        val cashRepository: CashRepository[IO] = CashRepository.InMemory(cashRepositoryStateRef)
        AtomicCell[IO].of(cashRepository)
      atm = Atm.impl[IO](
        atomicCashRepository,
        cashDispenser,
        cashPrinter,
        config.timeout,
      )
    yield ExitCode.Success
