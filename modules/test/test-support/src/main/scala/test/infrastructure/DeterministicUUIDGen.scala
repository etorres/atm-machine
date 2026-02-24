package es.eriktorr
package test.infrastructure

import test.infrastructure.DeterministicUUIDGen.UUIDGenState
import test.stubs.{InMemoryState, SuccessPathProviderStub}

import cats.effect.std.UUIDGen
import cats.effect.{IO, Ref}

import java.util.UUID

final class DeterministicUUIDGen(
    val stateRef: Ref[IO, UUIDGenState],
) extends UUIDGen[IO]
    with SuccessPathProviderStub[UUIDGenState, List[UUID]]:
  override def randomUUID: IO[UUID] = stateRef.flatModify { currentState =>
    val (headIO, next) = currentState.value match
      case ::(head, next) => (IO.pure(head), next)
      case Nil => (IO.raiseError(IllegalStateException("UUIDs exhausted")), List.empty)
    (currentState.copy(next), headIO)
  }

object DeterministicUUIDGen:
  final case class UUIDGenState(
      value: List[UUID],
  ) extends InMemoryState[UUIDGenState, List[UUID]]:
    override def set(
        newValue: List[UUID],
    ): UUIDGenState =
      copy(newValue)

  object UUIDGenState:
    val empty: UUIDGenState = UUIDGenState(List.empty)
