package es.eriktorr
package test.infrastructure

import test.infrastructure.DeterministicClock.ClockState
import test.stubs.{InMemoryState, SuccessPathProviderStub}

import cats.Applicative
import cats.effect.{Clock, IO, Ref}

import java.time.Instant
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

final class DeterministicClock(
    val stateRef: Ref[IO, ClockState],
) extends Clock[IO]
    with SuccessPathProviderStub[ClockState, List[Instant]]:
  override def applicative: Applicative[IO] = Applicative[IO]

  override def monotonic: IO[FiniteDuration] = nextInstant

  override def realTime: IO[FiniteDuration] = nextInstant

  private def nextInstant = stateRef.flatModify { currentState =>
    val (headIO, next) = currentState.value match
      case ::(head, next) => (IO.pure(head), next)
      case Nil => (IO.raiseError(IllegalStateException("Instants exhausted")), List.empty)
    (currentState.copy(next), headIO.map(head => FiniteDuration(head.toEpochMilli, MILLISECONDS)))
  }

object DeterministicClock:
  final case class ClockState(
      value: List[Instant],
  ) extends InMemoryState[ClockState, List[Instant]]:
    override def set(
        newValue: List[Instant],
    ): ClockState =
      copy(newValue)

  object ClockState:
    val empty: ClockState = ClockState(List.empty)
