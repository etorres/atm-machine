package es.eriktorr
package atm.application.support

import atm.application.AtmApplicationService
import atm.application.support.AtmTestRunner.Stubs
import atm.application.types.StubStates
import atm.domain.*
import atm.domain.AccountRepositoryStub.AccountRepositoryState
import atm.domain.AtmRepositoryStub.AtmRepositoryState
import atm.domain.CashDispenserServiceStub.CashDispenserServiceState
import atm.domain.PhysicalDispenserStub.PhysicalDispenserState
import atm.domain.model.TerminalId
import atm.repository.TransactionAuditorStub
import atm.repository.TransactionAuditorStub.TransactionAuditorState
import test.infrastructure.DeterministicClock.ClockState
import test.infrastructure.DeterministicUUIDGen.UUIDGenState
import test.infrastructure.{DeterministicClock, DeterministicUUIDGen}
import test.utils.FailureRateSampler.failureRateSamplerGen
import test.utils.GenExtensions.sampleWithSeed
import test.utils.{FailureRate, FailureRateSampler, SimulatedFailure}

import cats.effect.std.{AtomicCell, UUIDGen}
import cats.effect.{Clock, IO, Ref}
import cats.implicits.*
import cats.mtl.{Handle, Raise}
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

import scala.reflect.ClassTag

abstract class AtmTestRunner extends CatsEffectSuite with ScalaCheckEffectSuite:
  final def withService[R, E <: Throwable: ClassTag](
      terminalId: TerminalId,
      initialState: StubStates,
  )(
      test: Raise[IO, E] ?=> (AtmApplicationService[IO], Stubs) => IO[R],
  ): IO[(Either[E, R], StubStates)] =
    for
      given FailureRateSampler =
        failureRateSamplerGen.sampleWithSeed(verbose = false)
      accountRepositoryStub <- (
        Ref.of[IO, AccountRepositoryState](initialState.accountRepositoryState),
        Ref.of[IO, FailureRate](FailureRate.alwaysSucceed),
      ).mapN(AccountRepositoryStub.apply)
      atmRepositoryStub <- (
        Ref.of[IO, AtmRepositoryState](initialState.atmRepositoryState),
        Ref.of[IO, FailureRate](FailureRate.alwaysSucceed),
      ).mapN(AtmRepositoryStub.apply)
      cashDispenserServiceStub <- (
        Ref.of[IO, CashDispenserServiceState](initialState.cashDispenserServiceState),
        Ref.of[IO, FailureRate](FailureRate.alwaysSucceed),
      ).mapN(CashDispenserServiceStub.apply)
      clockStub <-
        Ref
          .of[IO, ClockState](initialState.clockState)
          .map(DeterministicClock.apply)
      physicalDispenserStub <- (
        Ref.of[IO, PhysicalDispenserState](initialState.physicalDispenserState),
        Ref.of[IO, FailureRate](FailureRate.alwaysSucceed),
      ).mapN(PhysicalDispenserStub.apply)
      transactionAuditorStub <-
        Ref
          .of[IO, TransactionAuditorState](initialState.transactionAuditorState)
          .map(TransactionAuditorStub.apply)
      uuidGenStub <-
        Ref
          .of[IO, UUIDGenState](initialState.uuidGenState)
          .map(DeterministicUUIDGen.apply)
      atomicRepositories <- AtomicCell[IO].of[
        (AccountRepository[IO], AtmRepository[IO]),
      ](accountRepositoryStub, atmRepositoryStub)
      atmApplicationService =
        given Clock[IO] = clockStub
        given Logger[IO] = NoOpLogger.impl[IO]
        given UUIDGen[IO] = uuidGenStub
        AtmApplicationService[IO](
          atomicRepositories,
          transactionAuditorStub,
          cashDispenserServiceStub,
          physicalDispenserStub,
          terminalId,
        )
      stubs = Stubs(
        accountRepositoryStub,
        atmRepositoryStub,
        cashDispenserServiceStub,
        clockStub,
        physicalDispenserStub,
        transactionAuditorStub,
        uuidGenStub,
      )
      result <- Handle
        .allow[E]:
          test(atmApplicationService, stubs)
            .map(_.asRight)
        .rescue: error =>
          IO.pure(error.asLeft)
        .handleErrorWith:
          case handledError: E => IO.raiseError(handledError)
          case simulatedFailure: SimulatedFailure.type =>
            IO.raiseError(simulatedFailure)
          case other =>
            IO.blocking(other.printStackTrace())
              *> IO.raiseError(other)
      finalState <- stubs.state
    yield result -> finalState

object AtmTestRunner:
  final case class Stubs(
      accountRepositoryStub: AccountRepositoryStub,
      atmRepositoryStub: AtmRepositoryStub,
      cashDispenserServiceStub: CashDispenserServiceStub,
      clockStub: DeterministicClock,
      physicalDispenserStub: PhysicalDispenserStub,
      transactionAuditorStub: TransactionAuditorStub,
      uuidGenStub: DeterministicUUIDGen,
  ):
    def state: IO[StubStates] =
      (
        accountRepositoryStub.stateRef.get,
        atmRepositoryStub.stateRef.get,
        cashDispenserServiceStub.stateRef.get,
        clockStub.stateRef.get,
        physicalDispenserStub.stateRef.get,
        transactionAuditorStub.stateRef.get,
        uuidGenStub.stateRef.get,
      ).mapN(StubStates.apply)
