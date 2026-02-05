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
  /** Loads native libraries for Google OR-Tools, which can be slow due to disk I/O, classpath
    * scanning, or antivirus interference.
    *
    * Calling once per JVM process, early can help to minimize its impact. You can move it to your
    * application startup or a static initializer.
    *
    * @param verbose
    *   Print Google OR-Tools version to logs.
    * @tparam F
    *   Type parameter.
    * @return
    *   Nothing.
    */
  def init[F[_]: {Logger, Sync}](
      verbose: Boolean = false,
  ): F[Unit] =
    def loadNativeLibraries =
      Sync[F].delay(Loader.loadNativeLibraries())

    def showVersion =
      Sync[F]
        .pure(verbose)
        .ifM(
          ifTrue =
            Logger[F].info(show"Google OR-Tools version: ${OrToolsVersion.getVersionString}"),
          ifFalse = Sync[F].unit,
        )

    loadNativeLibraries >> showVersion
  end init

  /** Build a solver and return it wrapped in `F`.
    * @param solver
    *   Solver model.
    * @param timeLimitSeconds
    *   Stop after `timeLimitSeconds` seconds and return the best solution found so far.
    * @param multiCore
    *   Use multiple cores for faster search.
    * @tparam F
    *   Type parameter.
    * @return
    *   A new solver wrapped in `F`.
    */
  def make[F[_]: Sync](
      solver: Solver,
      multiCore: Boolean = true,
      timeLimitSeconds: Int = 10,
  ): F[MPSolver] =
    def createSolver =
      for
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

    def clear(solver: MPSolver): F[Unit] =
      for
        _ <- Sync[F].blocking(solver.clear())
        _ <- Sync[F]
          .pure(solver.variables().isEmpty && solver.constraints().isEmpty)
          .ifM(
            ifTrue = Sync[F].unit,
            ifFalse = Sync[F].raiseError(IllegalStateException("Failed to initialize the solver")),
          )
      yield ()

    createSolver.flatTap(clear)
  end make

  enum Solver(val id: String) derives Show:
    case SCIP extends Solver("SCIP")
