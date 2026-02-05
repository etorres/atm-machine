package es.eriktorr
package atm.infrastructure

import atm.infrastructure.FakeClock.ClockState

import cats.Applicative
import cats.effect.{Clock, IO, Ref}

import java.time.Instant
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

final class FakeClock(
    stateRef: Ref[IO, ClockState],
) extends Clock[IO]:
  override def applicative: Applicative[IO] = Applicative[IO]

  override def monotonic: IO[FiniteDuration] = nextInstant

  override def realTime: IO[FiniteDuration] = nextInstant

  private def nextInstant = stateRef.flatModify { currentState =>
    val (headIO, next) = currentState.instants match
      case ::(head, next) => (IO.pure(head), next)
      case Nil => (IO.raiseError(IllegalStateException("Instants exhausted")), List.empty)
    (currentState.copy(next), headIO.map(head => FiniteDuration(head.toEpochMilli, MILLISECONDS)))
  }

object FakeClock:
  final case class ClockState(instants: List[Instant]):
    def setInstants(newInstants: List[Instant]): ClockState =
      copy(newInstants)

  object ClockState:
    val empty: ClockState = ClockState(List.empty)
