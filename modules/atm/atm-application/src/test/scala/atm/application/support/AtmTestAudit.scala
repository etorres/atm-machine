package es.eriktorr
package atm.application.support

import atm.application.types.StubStates
import atm.domain.model.AccountId
import atm.domain.model.types.TransactionState
import cash.domain.model.Money

import cats.implicits.*
import squants.market.Currency

trait AtmTestAudit:
  final def verifyFundsInvariants(
      accountId: AccountId,
      money: Money,
      initialState: StubStates,
      finalState: StubStates,
  ): Unit =
    val initialBalance = balanceFrom(accountId, initialState)
    val finalBalance = balanceFrom(accountId, finalState)
    val initialCash = cashFrom(money.currency, initialState)
    val finalCash = cashFrom(money.currency, finalState)
    val dispensedMoney = dispensedMoneyFrom(finalState)

    val totalFundsUnchanged =
      initialBalance - money.amount + initialCash - dispensedMoney === finalBalance + finalCash
    val balanceUnchanged = initialBalance - money.amount === finalBalance
    val cashUnchanged = initialCash - dispensedMoney === finalCash

    assert(totalFundsUnchanged, "Total funds invariant after withdrawal")
    assert(balanceUnchanged, "Balance after withdrawal is correct")
    assert(cashUnchanged, "Cash after withdrawal is correct")

    verifyTransactionStates(
      accountId,
      money,
      finalState,
      TransactionState.Completed.some,
      List(
        TransactionState.Refunding,
        TransactionState.Refunded,
        TransactionState.ManualInterventionRequired,
      ),
    )

  final def verifyFundsUnchanged(
      accountId: AccountId,
      money: Money,
      initialState: StubStates,
      finalState: StubStates,
      maybeFinalTransactionState: Option[TransactionState],
  ): Unit =
    val initialBalance = balanceFrom(accountId, initialState)
    val finalBalance = balanceFrom(accountId, finalState)
    val initialCash = cashFrom(money.currency, initialState)
    val finalCash = cashFrom(money.currency, finalState)

    val totalFundsUnchanged = initialBalance + initialCash === finalBalance + finalCash
    val balanceUnchanged = initialBalance === finalBalance
    val cashUnchanged = initialCash === finalCash

    assert(totalFundsUnchanged, "Total funds invariant after unsuccessful withdrawal")
    assert(balanceUnchanged, "Balance after unsuccessful withdrawal is correct")
    assert(cashUnchanged, "Cash after unsuccessful withdrawal is correct")

    verifyTransactionStates(
      accountId,
      money,
      finalState,
      maybeFinalTransactionState,
      List(TransactionState.Completed),
    )

  final def verifyFundsInconsistency(
      accountId: AccountId,
      money: Money,
      initialState: StubStates,
      finalState: StubStates,
  ): Unit =
    val initialBalance = balanceFrom(accountId, initialState)
    val finalBalance = balanceFrom(accountId, finalState)
    val initialCash = cashFrom(money.currency, initialState)
    val finalCash = cashFrom(money.currency, finalState)

    val totalFundsChanged =
      initialBalance - money.amount + initialCash === finalBalance + finalCash
    val balanceChanged = initialBalance - money.amount === finalBalance
    val cashUnchanged = initialCash === finalCash

    assert(totalFundsChanged, "Total funds changed after unsuccessful refund")
    assert(balanceChanged, "Balance after unsuccessful refund is changed")
    assert(cashUnchanged, "Cash after unsuccessful refund is correct")

    verifyTransactionStates(
      accountId,
      money,
      finalState,
      TransactionState.ManualInterventionRequired.some,
      List(TransactionState.Refunded, TransactionState.Completed),
    )

  final def verifyCashInconsistency(
      accountId: AccountId,
      money: Money,
      initialState: StubStates,
      finalState: StubStates,
  ): Unit =
    val initialBalance = balanceFrom(accountId, initialState)
    val finalBalance = balanceFrom(accountId, finalState)
    val initialCash = cashFrom(money.currency, initialState)
    val finalCash = cashFrom(money.currency, finalState)

    val totalFundsChanged =
      initialBalance - money.amount + initialCash - money.amount === finalBalance + finalCash
    val balanceUnchanged = initialBalance - money.amount === finalBalance
    val cashChanged = initialCash - money.amount === finalCash

    assert(totalFundsChanged, "Total funds changed after unsuccessful delivery")
    assert(balanceUnchanged, "Balance after unsuccessful delivery is correct")
    assert(cashChanged, "Cash after unsuccessful delivery is correct")

    verifyTransactionStates(
      accountId,
      money,
      finalState,
      TransactionState.Debited.some,
    )

  private def balanceFrom(
      accountId: AccountId,
      stubStates: StubStates,
  ) =
    stubStates.accountRepositoryState.value
      .getOrElse(accountId, BigDecimal(0))

  private def cashFrom(
      currency: Currency,
      stubStates: StubStates,
  ) =
    stubStates.atmRepositoryState.value
      .getOrElse(currency, Map.empty)
      .map(_ * _)
      .map(BigDecimal.apply)
      .sum

  private def dispensedMoneyFrom(
      stubStates: StubStates,
  ) =
    stubStates.physicalDispenserState.value.headOption
      .getOrElse(Map.empty)
      .map(_ * _)
      .map(BigDecimal.apply)
      .sum

  private lazy val allTransactions =
    TransactionState.values.toList

  private def verifyTransactionStates(
      accountId: AccountId,
      money: Money,
      finalState: StubStates,
      maybeFinalTransactionState: Option[TransactionState],
      skippedTransactionStates: List[TransactionState] = List.empty,
  ): Unit =
    val auditEntries =
      finalState.transactionAuditorState.value
        .filter: entry =>
          entry.accountId == accountId && entry.money == money
        .sortBy: entry =>
          (entry.timestamp, entry.state)
    val actualTransactionStates =
      auditEntries.groupMap(_.id)(_.state).values.flatten.toList
    val allowedTransactions = allTransactions.diff(skippedTransactionStates)
    val expectedTransactionStates =
      maybeFinalTransactionState match
        case Some(state) =>
          val idx = allowedTransactions.indexOf(state)
          if idx >= 0 then allowedTransactions.take(idx + 1) else allowedTransactions
        case None => List.empty

    assert(
      actualTransactionStates == expectedTransactionStates,
      "State transactions were recorded",
    )
