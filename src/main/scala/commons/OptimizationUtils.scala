package es.eriktorr
package commons

import cats.Show
import cats.effect.Sync
import cats.implicits.*
import com.google.ortools.Loader
import com.google.ortools.init.OrToolsVersion
import com.google.ortools.linearsolver.MPSolver
import org.typelevel.log4cats.Logger

object OptimizationUtils:
  def create[F[_]: {Logger, Sync}](
      solver: Solver,
      verbose: Boolean = false,
  ): F[MPSolver] =
    def createSolver =
      for
        _ <- Sync[F].delay(Loader.loadNativeLibraries())
        solver <- Sync[F].fromOption(
          Option(MPSolver.createSolver(solver.id)),
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

  enum Solver(val id: String):
    case SCIP extends Solver("SCIP")

  object Solver:
    given Show[Solver] = Show.show(_.id)
