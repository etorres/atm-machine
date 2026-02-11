package es.eriktorr
package atm.domain

import atm.domain.AtmRepositoryStub.{AtmRepositoryState, CurrencyToCash}
import cash.domain.model.{Availability, Denomination, Quantity}
import test.stubs.{FailurePathProviderStub, InMemoryState}
import test.utils.{FailureRate, FailureRateSampler, SimulatedFailure}

import cats.effect.{IO, Ref}
import squants.market.Currency

final class AtmRepositoryStub(
    val stateRef: Ref[IO, AtmRepositoryState],
    val failureRateRef: Ref[IO, FailureRate],
)(using
    failureRateSampler: FailureRateSampler,
) extends AtmRepository[IO]
    with FailurePathProviderStub[AtmRepositoryState, CurrencyToCash]:
  override def getAvailableCashIn(
      currency: Currency,
  ): IO[Map[Denomination, Availability]] =
    stateRef.get.map:
      _.value.getOrElse(currency, Map.empty)

  override def decrementInventory(
      currency: Currency,
      dispensedNotes: Map[Denomination, Quantity],
  ): IO[Unit] =
    attemptOrRaiseError(
      onSuccess = stateRef.update: currentState =>
        val newAvailabilities = currentState.value
          .getOrElse(currency, Map.empty)
          .map:
            case (denomination, availability) =>
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
        currentState.set(currentState.value + (currency -> newAvailabilities))
      ,
      onFailure = SimulatedFailure,
    )

object AtmRepositoryStub:
  type CurrencyToCash = Map[Currency, Map[Denomination, Availability]]

  final case class AtmRepositoryState(
      value: CurrencyToCash,
  ) extends InMemoryState[AtmRepositoryState, CurrencyToCash]:
    def set(
        newValue: CurrencyToCash,
    ): AtmRepositoryState =
      copy(newValue)

  object AtmRepositoryState:
    val empty: AtmRepositoryState = AtmRepositoryState(Map.empty)
