package es.eriktorr
package atm.infrastructure

import atm.infrastructure.FakeUUIDGen.UUIDGenState

import cats.effect.std.UUIDGen
import cats.effect.{IO, Ref}

import java.util.UUID

final class FakeUUIDGen(
    stateRef: Ref[IO, UUIDGenState],
) extends UUIDGen[IO]:
  override def randomUUID: IO[UUID] = stateRef.flatModify { currentState =>
    val (headIO, next) = currentState.uuids match
      case ::(head, next) => (IO.pure(head), next)
      case Nil => (IO.raiseError(IllegalStateException("UUIDs exhausted")), List.empty)
    (currentState.copy(next), headIO)
  }

object FakeUUIDGen:
  final case class UUIDGenState(uuids: List[UUID]):
    def one(newUuid: UUID): UUIDGenState =
      setUUIDs(List(newUuid))

    def setUUIDs(newUuids: List[UUID]): UUIDGenState =
      copy(uuids = newUuids)

  object UUIDGenState:
    val empty: UUIDGenState = UUIDGenState(List.empty)
