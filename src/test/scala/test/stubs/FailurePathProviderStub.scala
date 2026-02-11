package es.eriktorr
package test.stubs

import test.utils.{FailureRate, FailureRateSampler}

import cats.effect.{IO, Ref}
import cats.mtl.Raise
import cats.mtl.implicits.given

trait FailurePathProviderStub[State <: InMemoryState[State, A], A]
    extends SuccessPathProviderStub[State, A]:
  protected val failureRateRef: Ref[IO, FailureRate]

  def setFailureRate(newFailureRate: FailureRate): IO[Unit] =
    failureRateRef.set(newFailureRate)

  protected def attemptOrRaise[B, E](
      onSuccess: IO[B],
      onFailure: E,
  )(using
      failureRateSampler: FailureRateSampler,
      raiseE: Raise[IO, E],
  ): IO[B] =
    failureRateRef.get.flatMap: failureRate =>
      IO.pure(failureRateSampler.next(failureRate) < failureRate)
        .ifM(
          ifTrue = onFailure.raise,
          ifFalse = onSuccess,
        )

  protected def attemptOrRaiseError[B, E <: Throwable](
      onSuccess: IO[B],
      onFailure: E,
  )(using
      failureRateSampler: FailureRateSampler,
  ): IO[B] =
    failureRateRef.get.flatMap: failureRate =>
      IO.pure(failureRateSampler.next(failureRate) < failureRate)
        .ifM(
          ifTrue = IO.raiseError(onFailure),
          ifFalse = onSuccess,
        )
