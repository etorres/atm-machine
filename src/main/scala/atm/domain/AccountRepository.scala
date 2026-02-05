package es.eriktorr
package atm.domain

import cash.domain.model.AccountId

import cats.effect.{Async, Sync}
import cats.effect.std.MapRef
import cats.implicits.*

trait AccountRepository[F[_]]:
  def getBalance(
      accountId: AccountId,
  ): F[BigDecimal]

  def debit(
      accountId: AccountId,
      amount: BigDecimal,
  ): F[Unit]

object AccountRepository:
  def make[F[_]: Async]: F[AccountRepository[F]] =
    MapRef[F, AccountId, BigDecimal].map: mapRef =>
      InMemory[F](mapRef)

  final class InMemory[F[_]: Sync](
      mapRef: MapRef[F, AccountId, Option[BigDecimal]],
  ) extends AccountRepository[F]:
    override def getBalance(
        accountId: AccountId,
    ): F[BigDecimal] =
      mapRef(accountId).get.map: maybeBalance =>
        maybeBalance.getOrElse(BigDecimal(0))

    override def debit(
        accountId: AccountId,
        amount: BigDecimal,
    ): F[Unit] =
      mapRef(accountId).update: maybeBalance =>
        maybeBalance
          .orElse(Some(BigDecimal(0)))
          .map: currentBalance =>
            currentBalance - amount
