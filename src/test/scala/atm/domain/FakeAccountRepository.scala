package es.eriktorr
package atm.domain

import atm.domain.FakeAccountRepository.AccountRepositoryState
import cash.domain.model.AccountId

import cats.effect.{IO, Ref}

final class FakeAccountRepository(
    stateRef: Ref[IO, AccountRepositoryState],
) extends AccountRepository[IO]:
  override def getBalance(
      accountId: AccountId,
  ): IO[BigDecimal] =
    stateRef.get.map:
      _.accounts.getOrElse(accountId, BigDecimal(0))

  override def debit(
      accountId: AccountId,
      amount: BigDecimal,
  ): IO[Unit] =
    stateRef.update: currentState =>
      val currentBalance = currentState.accounts
        .getOrElse(accountId, BigDecimal(0))
      val updatedBalance = currentBalance - amount
      currentState.copy(currentState.accounts + (accountId -> updatedBalance))

object FakeAccountRepository:
  final case class AccountRepositoryState(
      accounts: Map[AccountId, BigDecimal],
  ):
    def setAccounts(
        newAccounts: Map[AccountId, BigDecimal],
    ): AccountRepositoryState =
      copy(newAccounts)

  object AccountRepositoryState:
    val empty: AccountRepositoryState = AccountRepositoryState(Map.empty)
