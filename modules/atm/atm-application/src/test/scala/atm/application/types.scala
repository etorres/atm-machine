package es.eriktorr
package atm.application

import atm.domain.AccountRepositoryStub.{AccountRepositoryState, Accounts}
import atm.domain.AtmRepositoryStub.{AtmRepositoryState, CurrencyToCash}
import atm.domain.CashDispenserServiceStub.{CashDispenserServiceState, Inventories}
import atm.domain.PhysicalDispenserStub.PhysicalDispenserState
import atm.domain.model.{AccountId, Receipt, TerminalId}
import atm.repository.TransactionAuditorStub.TransactionAuditorState
import cash.domain.model.*
import test.infrastructure.DeterministicClock.ClockState
import test.infrastructure.DeterministicUUIDGen.UUIDGenState

import java.time.Instant
import java.util.UUID

object types:
  final case class StubStates(
      accountRepositoryState: AccountRepositoryState,
      atmRepositoryState: AtmRepositoryState,
      cashDispenserServiceState: CashDispenserServiceState,
      clockState: ClockState,
      physicalDispenserState: PhysicalDispenserState,
      transactionAuditorState: TransactionAuditorState,
      uuidGenState: UUIDGenState,
  ):
    def setAccounts(accounts: Accounts): StubStates =
      copy(accountRepositoryState = accountRepositoryState.set(accounts))

    def setCashInventory(availabilities: CurrencyToCash): StubStates =
      copy(atmRepositoryState = atmRepositoryState.set(availabilities))

    def setInstants(instants: List[Instant]): StubStates =
      copy(clockState = clockState.set(instants))

    def setWithdrawableFunds(inventories: Inventories): StubStates =
      copy(cashDispenserServiceState = cashDispenserServiceState.set(inventories))

    def setUUIDs(uuids: List[UUID]): StubStates =
      copy(uuidGenState = uuidGenState.set(uuids))

  val emptyStubStates: StubStates =
    StubStates(
      AccountRepositoryState.empty,
      AtmRepositoryState.empty,
      CashDispenserServiceState.empty,
      ClockState.empty,
      PhysicalDispenserState.empty,
      TransactionAuditorState.empty,
      UUIDGenState.empty,
    )

  type TestCase = (
      terminalId: TerminalId,
      accountId: AccountId,
      money: Money,
      initialState: StubStates,
      expected: Option[Receipt],
  )
