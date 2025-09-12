package es.eriktorr
package atm

import cash.Cash.{Availability, Denomination, Quantity}
import cash.FakeCashDispenser.CashDispenserState
import cash.FakeCashPrinter.CashPrinterState
import cash.{CashRepository, FakeCashDispenser, FakeCashPrinter, FakeCashRepository}
import cash.FakeCashRepository.CashRepositoryState

import cats.effect.std.AtomicCell
import cats.effect.{IO, Ref}
import squants.market.Currency

object AtmSuiteRunner:
  final case class AtmSuiteState(
      cashDispenserState: CashDispenserState,
      cashPrinterState: CashPrinterState,
      cashRepositoryState: CashRepositoryState,
  ):
    def setAmounts(
        amounts: Map[(Quantity, Map[Denomination, Availability]), Map[Denomination, Quantity]],
    ): AtmSuiteState =
      copy(cashDispenserState = cashDispenserState.setAmounts(amounts))

    def setAvailabilities(
        availabilities: Map[Currency, Map[Denomination, Availability]],
    ): AtmSuiteState =
      copy(cashRepositoryState = cashRepositoryState.setAvailabilities(availabilities))

    def setLines(lines: List[String]): AtmSuiteState =
      copy(cashPrinterState = cashPrinterState.setLines(lines))

    def setRemoved(removed: List[(Currency, Map[Denomination, Quantity])]): AtmSuiteState =
      copy(cashRepositoryState = cashRepositoryState.setRemoved(removed))

  object AtmSuiteState:
    val empty: AtmSuiteState =
      AtmSuiteState(
        CashDispenserState.empty,
        CashPrinterState.empty,
        CashRepositoryState.empty,
      )

  def runWith[A](
      initialState: AtmSuiteState,
  )(run: Atm[IO] => IO[A]): IO[(Either[Throwable, A], AtmSuiteState)] =
    for
      cashDispenserStateRef <- Ref.of[IO, CashDispenserState](initialState.cashDispenserState)
      cashPrinterStateRef <- Ref.of[IO, CashPrinterState](initialState.cashPrinterState)
      cashRepositoryStateRef <- Ref.of[IO, CashRepositoryState](initialState.cashRepositoryState)
      repositoryRef <- AtomicCell[IO].of[CashRepository[IO]](
        FakeCashRepository(cashRepositoryStateRef),
      )
      atm = Atm.impl[IO](
        repositoryRef,
        FakeCashDispenser(cashDispenserStateRef),
        FakeCashPrinter(cashPrinterStateRef),
      )
      result <- run(atm).attempt
      _ = result match
        case Left(error) => error.printStackTrace()
        case _ => ()
      finalCashDispenserState <- cashDispenserStateRef.get
      finaCashPrinterState <- cashPrinterStateRef.get
      finalCashRepositoryState <- cashRepositoryStateRef.get
      finalState = initialState.copy(
        cashDispenserState = finalCashDispenserState,
        cashPrinterState = finaCashPrinterState,
        cashRepositoryState = finalCashRepositoryState,
      )
    yield result -> finalState
