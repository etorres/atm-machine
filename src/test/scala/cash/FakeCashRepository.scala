package es.eriktorr
package cash

import cash.Cash.{Availability, Denomination, Quantity}
import cash.FakeCashRepository.CashRepositoryState

import cats.effect.{IO, Ref}
import squants.market.Currency

final class FakeCashRepository(stateRef: Ref[IO, CashRepositoryState]) extends CashRepository[IO]:
  override def availabilityIn(currency: Currency): IO[Map[Denomination, Availability]] =
    stateRef.get.map(
      _.availabilities.getOrElse(currency, Map.empty),
    )

  override def remove(
      currency: Currency,
      amounts: Map[Denomination, Quantity],
  ): IO[Unit] =
    stateRef.update: currentState =>
      currentState.copy(removed = (currency -> amounts) :: currentState.removed)

object FakeCashRepository:
  final case class CashRepositoryState(
      availabilities: Map[Currency, Map[Denomination, Availability]],
      removed: List[(Currency, Map[Denomination, Quantity])],
  ):
    def setAvailabilities(
        newAvailabilities: Map[Currency, Map[Denomination, Availability]],
    ): CashRepositoryState =
      copy(availabilities = newAvailabilities)

    def setRemoved(newRemoved: List[(Currency, Map[Denomination, Quantity])]): CashRepositoryState =
      copy(removed = newRemoved)

  object CashRepositoryState:
    val empty = CashRepositoryState(Map.empty, List.empty)
