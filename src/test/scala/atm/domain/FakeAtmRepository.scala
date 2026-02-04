package es.eriktorr
package atm.domain

import atm.domain.FakeAtmRepository.AtmRepositoryState
import cash.domain.{Availability, Denomination, Quantity}

import cats.effect.{IO, Ref}
import squants.market.Currency

final class FakeAtmRepository(
    stateRef: Ref[IO, AtmRepositoryState],
) extends AtmRepository[IO]:
  override def getAvailableCashIn(
      currency: Currency,
  ): IO[Map[Denomination, Availability]] =
    stateRef.get.map:
      _.availabilities.getOrElse(currency, Map.empty)

  override def decrementInventory(
      currency: Currency,
      dispensedNotes: Map[Denomination, Quantity],
  ): IO[Unit] =
    stateRef.update: currentState =>
      currentState.copy(removed = (currency -> dispensedNotes) :: currentState.removed)

object FakeAtmRepository:
  final case class AtmRepositoryState(
      availabilities: Map[Currency, Map[Denomination, Availability]],
      removed: List[(Currency, Map[Denomination, Quantity])],
  ):
    def setAvailabilities(
        newAvailabilities: Map[Currency, Map[Denomination, Availability]],
    ): AtmRepositoryState =
      copy(availabilities = newAvailabilities)

    def setRemoved(
        newRemoved: List[(Currency, Map[Denomination, Quantity])],
    ): AtmRepositoryState =
      copy(removed = newRemoved)

  object AtmRepositoryState:
    val empty: AtmRepositoryState = AtmRepositoryState(Map.empty, List.empty)
