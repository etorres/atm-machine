package es.eriktorr
package atm.application

import atm.domain.*
import atm.domain.FakeAccountRepository.AccountRepositoryState
import atm.domain.FakeAtmRepository.AtmRepositoryState
import atm.domain.FakeCashDispenserService.CashDispenserServiceState
import atm.domain.FakePhysicalDispenser.PhysicalDispenserState
import atm.domain.model.types.AuditEntry
import atm.infrastructure.FakeClock.ClockState
import atm.infrastructure.FakeUUIDGen.UUIDGenState
import atm.infrastructure.{FakeClock, FakeUUIDGen}
import atm.repository.FakeTransactionAuditor
import atm.repository.FakeTransactionAuditor.TransactionAuditorState
import cash.domain.model.*

import cats.effect.std.{AtomicCell, UUIDGen}
import cats.effect.{Clock, IO, Ref}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import squants.market.Currency

import java.time.Instant
import java.util.UUID

object AtmApplicationServiceSuiteRunner:
  final case class AtmApplicationServiceState(
      accountRepositoryState: AccountRepositoryState,
      atmRepositoryState: AtmRepositoryState,
      cashDispenserServiceState: CashDispenserServiceState,
      clockState: ClockState,
      physicalDispenserState: PhysicalDispenserState,
      transactionAuditorState: TransactionAuditorState,
      uuidGenState: UUIDGenState,
  ):
    def cleanInstants: AtmApplicationServiceState =
      copy(clockState = ClockState.empty)

    def clearUUIDs: AtmApplicationServiceState =
      copy(uuidGenState = UUIDGenState.empty)

    def setAccounts(accounts: Map[AccountId, BigDecimal]): AtmApplicationServiceState =
      copy(accountRepositoryState = accountRepositoryState.setAccounts(accounts))

    def setAvailabilities(
        availabilities: Map[Currency, Map[Denomination, Availability]],
    ): AtmApplicationServiceState =
      copy(atmRepositoryState = atmRepositoryState.setAvailabilities(availabilities))

    def setAuditEntries(auditEntries: List[AuditEntry]): AtmApplicationServiceState =
      copy(transactionAuditorState = transactionAuditorState.setAuditEntries(auditEntries))

    def setInstants(instants: List[Instant]): AtmApplicationServiceState =
      copy(clockState = clockState.setInstants(instants))

    def setRemoved(
        removed: List[(Currency, Map[Denomination, Quantity])],
    ): AtmApplicationServiceState =
      copy(atmRepositoryState = atmRepositoryState.setRemoved(removed))

    def setInventories(
        inventories: Map[
          (Money.Amount, Map[Denomination, Availability]),
          Map[Denomination, Quantity],
        ],
    ): AtmApplicationServiceState =
      copy(cashDispenserServiceState = cashDispenserServiceState.setInventories(inventories))

    def setNotes(
        notes: List[Map[Denomination, Quantity]],
    ): AtmApplicationServiceState =
      copy(physicalDispenserState = physicalDispenserState.setNotes(notes))

    def setUUIDs(uuids: List[UUID]): AtmApplicationServiceState =
      copy(uuidGenState = uuidGenState.setUUIDs(uuids))
  end AtmApplicationServiceState

  object AtmApplicationServiceState:
    val empty: AtmApplicationServiceState =
      AtmApplicationServiceState(
        AccountRepositoryState.empty,
        AtmRepositoryState.empty,
        CashDispenserServiceState.empty,
        ClockState.empty,
        PhysicalDispenserState.empty,
        TransactionAuditorState.empty,
        UUIDGenState.empty,
      )

  def runWith[A](
      initialState: AtmApplicationServiceState,
  )(
      run: AtmApplicationService[IO] => IO[A],
  ): IO[(Either[Throwable, A], AtmApplicationServiceState)] =
    for
      accountRepositoryStateRef <- Ref.of[IO, AccountRepositoryState](
        initialState.accountRepositoryState,
      )
      atmRepositoryStateRef <- Ref.of[IO, AtmRepositoryState](
        initialState.atmRepositoryState,
      )
      atomicRepositories <- AtomicCell[IO].of[(AccountRepository[IO], AtmRepository[IO])](
        FakeAccountRepository(accountRepositoryStateRef),
        FakeAtmRepository(atmRepositoryStateRef),
      )
      cashDispenserServiceStateRef <- Ref.of[IO, CashDispenserServiceState](
        initialState.cashDispenserServiceState,
      )
      clockStateRef <- Ref.of[IO, ClockState](
        initialState.clockState,
      )
      physicalDispenserStateRef <- Ref.of[IO, PhysicalDispenserState](
        initialState.physicalDispenserState,
      )
      transactionAuditorStateRef <- Ref.of[IO, TransactionAuditorState](
        initialState.transactionAuditorState,
      )
      uuidGenStateRef <- Ref.of[IO, UUIDGenState](
        initialState.uuidGenState,
      )
      atmApplicationService =
        given Clock[IO] = FakeClock(clockStateRef)
        given Logger[IO] = NoOpLogger.impl[IO]
        given UUIDGen[IO] = FakeUUIDGen(uuidGenStateRef)
        AtmApplicationService[IO](
          atomicRepositories,
          FakeTransactionAuditor(transactionAuditorStateRef),
          FakeCashDispenserService(cashDispenserServiceStateRef),
          FakePhysicalDispenser(physicalDispenserStateRef),
        )
      result <- run(atmApplicationService).attempt
      _ = result match
        case Left(error) => error.printStackTrace()
        case _ => ()
      finalAccountRepositoryState <- accountRepositoryStateRef.get
      finalAtmRepositoryState <- atmRepositoryStateRef.get
      finalCashDispenserServiceState <- cashDispenserServiceStateRef.get
      finalClockState <- clockStateRef.get
      finalPhysicalDispenserState <- physicalDispenserStateRef.get
      finalTransactionAuditorState <- transactionAuditorStateRef.get
      finalUUIDGenState <- uuidGenStateRef.get
      finalState = initialState.copy(
        accountRepositoryState = finalAccountRepositoryState,
        atmRepositoryState = finalAtmRepositoryState,
        cashDispenserServiceState = finalCashDispenserServiceState,
        clockState = finalClockState,
        physicalDispenserState = finalPhysicalDispenserState,
        transactionAuditorState = finalTransactionAuditorState,
        uuidGenState = finalUUIDGenState,
      )
    yield result -> finalState
