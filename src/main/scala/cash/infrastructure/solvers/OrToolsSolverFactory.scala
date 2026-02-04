package es.eriktorr
package cash.infrastructure.solvers

import cats.Show
import cats.derived.*
import cats.effect.Sync
import cats.implicits.*
import com.google.ortools.Loader
import com.google.ortools.init.OrToolsVersion
import com.google.ortools.linearsolver.MPSolver
import org.typelevel.log4cats.Logger

object OrToolsSolverFactory:
  /** Build a solver and return it wrapped in `F`.
    * @param solver
    *   Solver model.
    * @param timeLimitSeconds
    *   Stop after `timeLimitSeconds` seconds and return the best solution found so far.
    * @param multiCore
    *   Use multiple cores for faster search.
    * @param verbose
    *   Print Google OR-Tools version to logs.
    * @tparam F
    *   Type parameter.
    * @return
    *   A new solver wrapped in `F`.
    */
  def make[F[_]: {Logger, Sync}](
      solver: Solver,
      multiCore: Boolean = true,
      timeLimitSeconds: Int = 10,
      verbose: Boolean = false,
  ): F[MPSolver] =
    def createSolver =
      for
        _ <- Sync[F].delay(Loader.loadNativeLibraries())
        availableCores <- Sync[F].delay:
          Runtime.getRuntime.availableProcessors()
        solver <- Sync[F].fromOption(
          Option(MPSolver.createSolver(solver.id))
            .tapEach: solver =>
              solver.setTimeLimit(timeLimitSeconds * 1_000L)
              if multiCore && solver.setNumThreads(availableCores) then ()
            .headOption,
          IllegalStateException(show"Could not create solver $solver"),
        )
      yield solver

    def init(solver: MPSolver): F[Unit] =
      for
        _ <- Sync[F].blocking(solver.clear())
        _ <- Sync[F]
          .pure(solver.variables().isEmpty && solver.constraints().isEmpty)
          .ifM(
            ifTrue = Sync[F].unit,
            ifFalse = Sync[F].raiseError(IllegalStateException("Failed to initialize the solver")),
          )
      yield ()

    def showVersion =
      Sync[F]
        .pure(verbose)
        .ifM(
          ifTrue =
            Logger[F].info(show"Google OR-Tools version: ${OrToolsVersion.getVersionString}"),
          ifFalse = Sync[F].unit,
        )

    for
      solver <- createSolver
      _ <- init(solver)
      _ <- showVersion
    yield solver

  enum Solver(val id: String) derives Show:
    case SCIP extends Solver("SCIP")
