package es.eriktorr
package atm.application

import atm.domain.AccountRepositoryStub.{AccountRepositoryState, Accounts}
import atm.domain.AtmRepositoryStub.{AtmRepositoryState, CurrencyToCash}
import atm.domain.CashDispenserServiceStub.{CashDispenserServiceState, Inventories}
import atm.domain.PhysicalDispenserStub.PhysicalDispenserState
import atm.repository.TransactionAuditorStub.TransactionAuditorState
import cash.domain.model.*

object types:
  final case class StubStates(
      accountRepositoryState: AccountRepositoryState,
      atmRepositoryState: AtmRepositoryState,
      cashDispenserServiceState: CashDispenserServiceState,
      physicalDispenserState: PhysicalDispenserState,
      transactionAuditorState: TransactionAuditorState,
  ):
    def setAccounts(accounts: Accounts): StubStates =
      copy(accountRepositoryState = accountRepositoryState.set(accounts))

    def setCashInventory(availabilities: CurrencyToCash): StubStates =
      copy(atmRepositoryState = atmRepositoryState.set(availabilities))

    def setWithdrawableFunds(inventories: Inventories): StubStates =
      copy(cashDispenserServiceState = cashDispenserServiceState.set(inventories))

  val emptyStubStates: StubStates =
    StubStates(
      AccountRepositoryState.empty,
      AtmRepositoryState.empty,
      CashDispenserServiceState.empty,
      PhysicalDispenserState.empty,
      TransactionAuditorState.empty,
    )

  type TestCase = (
      accountId: AccountId,
      money: Money,
      initialState: StubStates,
  )
