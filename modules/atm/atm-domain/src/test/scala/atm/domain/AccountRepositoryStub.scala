package es.eriktorr
package atm.domain

import atm.domain.AccountRepositoryStub.{AccountRepositoryState, Accounts}
import atm.domain.model.AccountId
import test.stubs.{FailurePathProviderStub, InMemoryState}
import test.utils.{FailureRate, FailureRateSampler, SimulatedFailure}

import cats.effect.{IO, Ref}

final class AccountRepositoryStub(
    val stateRef: Ref[IO, AccountRepositoryState],
    val failureRateRef: Ref[IO, FailureRate],
)(using
    failureRateSampler: FailureRateSampler,
) extends AccountRepository[IO]
    with FailurePathProviderStub[AccountRepositoryState, Accounts]:
  override def getBalance(
      accountId: AccountId,
  ): IO[BigDecimal] =
    stateRef.get.map:
      _.value.getOrElse(accountId, BigDecimal(0))

  override def debit(
      accountId: AccountId,
      amount: BigDecimal,
  ): IO[Unit] =
    update(accountId, -amount.abs)

  override def credit(
      accountId: AccountId,
      amount: BigDecimal,
  ): IO[Unit] =
    attemptOrRaiseError(
      onSuccess = update(accountId, amount.abs),
      onFailure = SimulatedFailure,
    )

  private def update(
      accountId: AccountId,
      amount: BigDecimal,
  ): IO[Unit] =
    stateRef.update: currentState =>
      val currentBalance = currentState.value
        .getOrElse(accountId, BigDecimal(0))
      val updatedBalance = currentBalance + amount
      currentState.copy(currentState.value + (accountId -> updatedBalance))

object AccountRepositoryStub:
  type Accounts = Map[AccountId, BigDecimal]

  final case class AccountRepositoryState(
      value: Accounts,
  ) extends InMemoryState[AccountRepositoryState, Accounts]:
    def set(
        newValue: Accounts,
    ): AccountRepositoryState =
      copy(newValue)

  object AccountRepositoryState:
    val empty: AccountRepositoryState = AccountRepositoryState(Map.empty)
