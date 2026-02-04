package es.eriktorr
package atm.application

import atm.domain.FakeAtmRepository.AtmRepositoryState
import atm.domain.FakeCashDispenserService.CashDispenserServiceState
import atm.domain.FakePhysicalDispenser.PhysicalDispenserState
import atm.domain.{
  AtmRepository,
  FakeAtmRepository,
  FakeCashDispenserService,
  FakePhysicalDispenser,
}
import cash.domain.Money.Amount
import cash.domain.{Availability, Denomination, Quantity}

import cats.effect.std.AtomicCell
import cats.effect.{IO, Ref}
import squants.market.Currency

object AtmApplicationServiceSuiteRunner:
  final case class AtmApplicationServiceState(
      atmRepositoryState: AtmRepositoryState,
      cashDispenserServiceState: CashDispenserServiceState,
      physicalDispenserState: PhysicalDispenserState,
  ):
    def setAvailabilities(
        availabilities: Map[Currency, Map[Denomination, Availability]],
    ): AtmApplicationServiceState =
      copy(atmRepositoryState = atmRepositoryState.setAvailabilities(availabilities))

    def setRemoved(
        removed: List[(Currency, Map[Denomination, Quantity])],
    ): AtmApplicationServiceState =
      copy(atmRepositoryState = atmRepositoryState.setRemoved(removed))

    def setInventories(
        inventories: Map[(Amount, Map[Denomination, Availability]), Map[Denomination, Quantity]],
    ): AtmApplicationServiceState =
      copy(cashDispenserServiceState = cashDispenserServiceState.setInventories(inventories))

    def setNotes(
        notes: List[Map[Denomination, Quantity]],
    ): AtmApplicationServiceState =
      copy(physicalDispenserState = physicalDispenserState.setNotes(notes))
  end AtmApplicationServiceState

  object AtmApplicationServiceState:
    val empty: AtmApplicationServiceState =
      AtmApplicationServiceState(
        AtmRepositoryState.empty,
        CashDispenserServiceState.empty,
        PhysicalDispenserState.empty,
      )

  def runWith[A](
      initialState: AtmApplicationServiceState,
  )(
      run: AtmApplicationService[IO] => IO[A],
  ): IO[(Either[Throwable, A], AtmApplicationServiceState)] =
    for
      atmRepositoryStateRef <- Ref.of[IO, AtmRepositoryState](
        initialState.atmRepositoryState,
      )
      cashDispenserServiceStateRef <- Ref.of[IO, CashDispenserServiceState](
        initialState.cashDispenserServiceState,
      )
      fakeAtomicAtmRepository <- AtomicCell[IO].of[AtmRepository[IO]](
        FakeAtmRepository(atmRepositoryStateRef),
      )
      physicalDispenserStateRef <- Ref.of[IO, PhysicalDispenserState](
        initialState.physicalDispenserState,
      )
      atmApplicationService = AtmApplicationService[IO](
        fakeAtomicAtmRepository,
        FakeCashDispenserService(cashDispenserServiceStateRef),
        FakePhysicalDispenser(physicalDispenserStateRef),
      )
      result <- run(atmApplicationService).attempt
      _ = result match
        case Left(error) => error.printStackTrace()
        case _ => ()
      finalAtmRepositoryState <- atmRepositoryStateRef.get
      finalCashDispenserServiceState <- cashDispenserServiceStateRef.get
      finalPhysicalDispenserState <- physicalDispenserStateRef.get
      finalState = initialState.copy(
        atmRepositoryState = finalAtmRepositoryState,
        cashDispenserServiceState = finalCashDispenserServiceState,
        physicalDispenserState = finalPhysicalDispenserState,
      )
    yield result -> finalState
