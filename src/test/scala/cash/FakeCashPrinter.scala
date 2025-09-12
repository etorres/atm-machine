package es.eriktorr
package cash

import cash.FakeCashPrinter.CashPrinterState

import cats.effect.{IO, Ref}

final class FakeCashPrinter(stateRef: Ref[IO, CashPrinterState]) extends CashPrinter[IO]:
  override def print(line: String): IO[Unit] =
    stateRef.update: currentState =>
      currentState.setLines(line :: currentState.lines)

object FakeCashPrinter:
  final case class CashPrinterState(lines: List[String]):
    def setLines(newLines: List[String]): CashPrinterState = copy(newLines)

  object CashPrinterState:
    val empty: CashPrinterState = CashPrinterState(List.empty)
