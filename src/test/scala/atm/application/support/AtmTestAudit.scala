package es.eriktorr
package atm.application.support

import atm.application.types.{StubStates, TestCase}
import atm.domain.model.types.TransactionState
import cash.domain.model.AccountId

import cats.implicits.*
import squants.market.Currency

trait AtmTestAudit:
  def verifyFundsInvariants(
      testCase: TestCase,
      finalState: StubStates,
  ): Unit =
    val initialBalance = balanceFrom(testCase.accountId, testCase.initialState)
    val finalBalance = balanceFrom(testCase.accountId, finalState)
    val initialCash = cashFrom(testCase.money.currency, testCase.initialState)
    val finalCash = cashFrom(testCase.money.currency, finalState)
    val dispensedMoney = dispensedMoneyFrom(finalState)

    val totalFundsUnchanged =
      initialBalance - testCase.money.amount + initialCash - dispensedMoney === finalBalance + finalCash
    val balanceUnchanged = initialBalance - testCase.money.amount === finalBalance
    val cashUnchanged = initialCash - dispensedMoney === finalCash

    assert(totalFundsUnchanged, "Total funds invariant after withdrawal")
    assert(balanceUnchanged, "Balance after withdrawal is correct")
    assert(cashUnchanged, "Cash after withdrawal is correct")

    verifyTransactionStates(
      testCase,
      finalState,
      TransactionState.Debited.some,
    )

  def verifyFundsUnchanged(
      testCase: TestCase,
      finalState: StubStates,
      maybeFinalTransactionState: Option[TransactionState] = None,
  ): Unit =
    val initialBalance = balanceFrom(testCase.accountId, testCase.initialState)
    val finalBalance = balanceFrom(testCase.accountId, finalState)
    val initialCash = cashFrom(testCase.money.currency, testCase.initialState)
    val finalCash = cashFrom(testCase.money.currency, finalState)

    val totalFundsUnchanged = initialBalance + initialCash === finalBalance + finalCash
    val balanceUnchanged = initialBalance === finalBalance
    val cashUnchanged = initialCash === finalCash

    assert(totalFundsUnchanged, "Total funds invariant after unsuccessful withdrawal")
    assert(balanceUnchanged, "Balance after unsuccessful withdrawal is correct")
    assert(cashUnchanged, "Cash after unsuccessful withdrawal is correct")

    verifyTransactionStates(
      testCase,
      finalState,
      maybeFinalTransactionState,
    )

  def verifyFundsInconsistency(
      testCase: TestCase,
      finalState: StubStates,
  ): Unit =
    val initialBalance = balanceFrom(testCase.accountId, testCase.initialState)
    val finalBalance = balanceFrom(testCase.accountId, finalState)
    val initialCash = cashFrom(testCase.money.currency, testCase.initialState)
    val finalCash = cashFrom(testCase.money.currency, finalState)

    val totalFundsChanged =
      initialBalance - testCase.money.amount + initialCash === finalBalance + finalCash
    val balanceChanged = initialBalance - testCase.money.amount === finalBalance
    val cashUnchanged = initialCash === finalCash

    assert(totalFundsChanged, "Total funds changed after unsuccessful refund")
    assert(balanceChanged, "Balance after unsuccessful refund is changed")
    assert(cashUnchanged, "Cash after unsuccessful refund is correct")

    verifyTransactionStates(
      testCase,
      finalState,
      TransactionState.ManualInterventionRequired.some,
      List(TransactionState.Refunded),
    )

  def verifyCashInconsistency(
      testCase: TestCase,
      finalState: StubStates,
  ): Unit =
    val initialBalance = balanceFrom(testCase.accountId, testCase.initialState)
    val finalBalance = balanceFrom(testCase.accountId, finalState)
    val initialCash = cashFrom(testCase.money.currency, testCase.initialState)
    val finalCash = cashFrom(testCase.money.currency, finalState)

    val totalFundsChanged =
      initialBalance - testCase.money.amount + initialCash - testCase.money.amount === finalBalance + finalCash
    val balanceUnchanged = initialBalance - testCase.money.amount === finalBalance
    val cashChanged = initialCash - testCase.money.amount === finalCash

    assert(totalFundsChanged, "Total funds changed after unsuccessful delivery")
    assert(balanceUnchanged, "Balance after unsuccessful delivery is correct")
    assert(cashChanged, "Cash after unsuccessful delivery is correct")

    verifyTransactionStates(
      testCase,
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

  private val allTransactions =
    List(
      TransactionState.Started,
      TransactionState.Debited,
      TransactionState.Refunding,
      TransactionState.Refunded,
      TransactionState.ManualInterventionRequired,
    )

  private def verifyTransactionStates(
      testCase: TestCase,
      finalState: StubStates,
      maybeFinalTransactionState: Option[TransactionState],
      skippedTransactionStates: List[TransactionState] = List.empty,
  ): Unit =
    val auditEntries =
      finalState.transactionAuditorState.value
        .filter: entry =>
          entry.accountId == testCase.accountId && entry.money == testCase.money
        .sortBy(_.timestamp)
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
