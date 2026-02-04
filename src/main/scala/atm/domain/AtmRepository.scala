package es.eriktorr
package atm.domain

import cash.domain.{Availability, Denomination, Quantity}

import cats.effect.std.MapRef
import cats.effect.{Async, Sync}
import cats.implicits.*
import squants.market.Currency

trait AtmRepository[F[_]]:
  def getAvailableCashIn(
      currency: Currency,
  ): F[Map[Denomination, Availability]]

  def decrementInventory(
      currency: Currency,
      dispensedNotes: Map[Denomination, Quantity],
  ): F[Unit]

object AtmRepository:
  def make[F[_]: Async]: F[AtmRepository[F]] =
    MapRef[F, Currency, Map[Denomination, Availability]].map: mapRef =>
      InMemory[F](mapRef)

  final class InMemory[F[_]: Sync](
      mapRef: MapRef[F, Currency, Option[Map[Denomination, Availability]]],
  ) extends AtmRepository[F]:
    override def getAvailableCashIn(
        currency: Currency,
    ): F[Map[Denomination, Availability]] =
      mapRef(currency).get.map: maybeAvailableCash =>
        maybeAvailableCash.getOrElse(Map.empty)

    override def decrementInventory(
        currency: Currency,
        dispensedNotes: Map[Denomination, Quantity],
    ): F[Unit] =
      mapRef(currency).update:
        _.map:
          _.map: (denomination, availability) =>
            val availabilitySubtrahend =
              dispensedNotes
                .get(denomination)
                .map(Availability.applyUnsafe)
                .getOrElse(Availability.Zero)
            val newAvailability =
              math.max(
                Availability.Zero,
                availability - availabilitySubtrahend,
              )
            denomination -> Availability.applyUnsafe(newAvailability)

  object InMemory:
    final case class AtmRepositoryState(
        availabilities: Map[Currency, Map[Denomination, Availability]],
    )

    object AtmRepositoryState:
      val empty: AtmRepositoryState = AtmRepositoryState(Map.empty)
