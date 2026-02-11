package es.eriktorr
package atm.application.support

import atm.application.AtmApplicationService
import atm.application.support.AtmTestRunner.Stubs
import atm.domain.*
import atm.domain.CashDispenserServiceStub.CashDispenserServiceState
import atm.domain.AccountRepositoryStub.AccountRepositoryState
import atm.domain.AtmRepositoryStub.AtmRepositoryState
import atm.domain.PhysicalDispenserStub.PhysicalDispenserState
import atm.repository.TransactionAuditorStub
import atm.repository.TransactionAuditorStub.TransactionAuditorState
import test.utils.FailureRateSampler.failureRateSamplerGen
import test.utils.GenExtensions.sampleWithSeed
import test.utils.{FailureRate, FailureRateSampler, SimulatedFailure}

import cats.effect.std.{AtomicCell, UUIDGen}
import cats.effect.{IO, Ref}
import cats.implicits.*
import cats.mtl.{Handle, Raise}
import es.eriktorr.atm.application.types.StubStates
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

import scala.reflect.ClassTag

abstract class AtmTestRunner extends CatsEffectSuite with ScalaCheckEffectSuite:
  def withService[R, E <: Throwable: ClassTag](
      test: Raise[IO, E] ?=> (AtmApplicationService[IO], Stubs) => IO[R],
  ): IO[(Either[Throwable, R], StubStates)] =
    for
      given FailureRateSampler =
        failureRateSamplerGen.sampleWithSeed(verbose = false)
      accountRepositoryStub <- (
        Ref.of[IO, AccountRepositoryState](AccountRepositoryState.empty),
        Ref.of[IO, FailureRate](FailureRate.alwaysSucceed),
      ).mapN(AccountRepositoryStub.apply)
      atmRepositoryStub <- (
        Ref.of[IO, AtmRepositoryState](AtmRepositoryState.empty),
        Ref.of[IO, FailureRate](FailureRate.alwaysSucceed),
      ).mapN(AtmRepositoryStub.apply)
      cashDispenserServiceStub <- (
        Ref.of[IO, CashDispenserServiceState](CashDispenserServiceState.empty),
        Ref.of[IO, FailureRate](FailureRate.alwaysSucceed),
      ).mapN(CashDispenserServiceStub.apply)
      physicalDispenserStub <- (
        Ref.of[IO, PhysicalDispenserState](PhysicalDispenserState.empty),
        Ref.of[IO, FailureRate](FailureRate.alwaysSucceed),
      ).mapN(PhysicalDispenserStub.apply)
      transactionAuditorStub <-
        Ref
          .of[IO, TransactionAuditorState](TransactionAuditorState.empty)
          .map(TransactionAuditorStub.apply)
      atomicRepositories <- AtomicCell[IO].of[
        (AccountRepository[IO], AtmRepository[IO]),
      ](accountRepositoryStub, atmRepositoryStub)
      atmApplicationService =
        given Logger[IO] = NoOpLogger.impl[IO]
        AtmApplicationService[IO](
          atomicRepositories,
          transactionAuditorStub,
          cashDispenserServiceStub,
          physicalDispenserStub,
        )
      stubs = Stubs(
        accountRepositoryStub,
        atmRepositoryStub,
        cashDispenserServiceStub,
        physicalDispenserStub,
        transactionAuditorStub,
      )
      result <- Handle
        .allow[E]:
          test(atmApplicationService, stubs)
        .rescue: error =>
          IO.raiseError(error)
        .attempt
      _ <- result match
        case Left(_: E) => IO.unit
        case Left(_: SimulatedFailure.type) => IO.unit
        case Left(error) => IO.blocking(error.printStackTrace())
        case _ => IO.unit
      finalState <- stubs.state
    yield result -> finalState

object AtmTestRunner:
  final case class Stubs(
      accountRepositoryStub: AccountRepositoryStub,
      atmRepositoryStub: AtmRepositoryStub,
      cashDispenserServiceStub: CashDispenserServiceStub,
      physicalDispenserStub: PhysicalDispenserStub,
      transactionAuditorStub: TransactionAuditorStub,
  ):
    def state: IO[StubStates] =
      (
        accountRepositoryStub.stateRef.get,
        atmRepositoryStub.stateRef.get,
        cashDispenserServiceStub.stateRef.get,
        physicalDispenserStub.stateRef.get,
        transactionAuditorStub.stateRef.get,
      ).mapN(StubStates.apply)
