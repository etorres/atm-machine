package es.eriktorr
package atm.infrastructure

import atm.infrastructure.FakeLinePrinter.LinePrinterState

import cats.effect.{IO, Ref}

final class FakeLinePrinter(
    stateRef: Ref[IO, LinePrinterState],
) extends LinePrinter[IO]:
  override def print(line: String): IO[Unit] =
    stateRef.update: currentState =>
      currentState.setLines(line :: currentState.lines)

object FakeLinePrinter:
  final case class LinePrinterState(lines: List[String]):
    def setLines(newLines: List[String]): LinePrinterState =
      copy(newLines)

  object LinePrinterState:
    val empty: LinePrinterState = LinePrinterState(List.empty)
