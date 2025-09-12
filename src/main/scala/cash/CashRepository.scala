package es.eriktorr
package cash

import cash.Cash.{Availability, Denomination, Quantity}
import cash.CashRepository.InMemory.CashRepositoryState

import cats.effect.{Ref, Sync}
import cats.implicits.*
import squants.market.Currency

trait CashRepository[F[_]]:
  def availabilityIn(currency: Currency): F[Map[Denomination, Availability]]
  def remove(currency: Currency, amounts: Map[Denomination, Quantity]): F[Unit]

object CashRepository:
  final class InMemory[F[_]: Sync](stateRef: Ref[F, CashRepositoryState]) extends CashRepository[F]:
    override def availabilityIn(currency: Currency): F[Map[Denomination, Availability]] =
      stateRef.get.map(
        _.availabilities.getOrElse(currency, Map.empty),
      )

    override def remove(currency: Currency, amounts: Map[Denomination, Quantity]): F[Unit] =
      stateRef.update: currentState =>
        currentState.availabilities.get(currency) match
          case Some(currentAvailabilities) =>
            val newAvailabilities = currentAvailabilities.map { case (denomination, availability) =>
              val availabilitySubtrahend = amounts
                .get(denomination)
                .map(amount => Availability.applyUnsafe(amount))
                .getOrElse(Availability.applyUnsafe(0))
              denomination -> Availability.applyUnsafe(availability - availabilitySubtrahend)
            }
            currentState.copy(currentState.availabilities + (currency -> newAvailabilities))
          case None => currentState

  object InMemory:
    final case class CashRepositoryState(
        availabilities: Map[Currency, Map[Denomination, Availability]],
    )

    object CashRepositoryState:
      val empty: CashRepositoryState = CashRepositoryState(Map.empty)
