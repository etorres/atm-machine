package es.eriktorr
package test.stubs

import cats.effect.{IO, Ref}

trait SuccessPathProviderStub[State <: InMemoryState[State, A], A]:
  protected val stateRef: Ref[IO, State]

  def setState(newState: State): IO[Unit] =
    stateRef.set(newState)
