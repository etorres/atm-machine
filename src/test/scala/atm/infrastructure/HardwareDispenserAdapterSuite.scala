package es.eriktorr
package atm.infrastructure

import atm.infrastructure.FakeLinePrinter.LinePrinterState
import atm.infrastructure.HardwareDispenserAdapterSuite.{testCaseGen, TestCase}
import cash.domain.model.CashGenerators.{denominationGen, quantityGen}
import cash.domain.model.{Denomination, Quantity}

import cats.effect.{IO, Ref, Resource}
import cats.implicits.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.scalacheck.Gen
import org.scalacheck.cats.implicits.given
import org.scalacheck.effect.PropF.forAllF

@SuppressWarnings(Array("org.wartremover.warts.Any"))
final class HardwareDispenserAdapterSuite extends CatsEffectSuite with ScalaCheckEffectSuite:
  withLinePrinter
    .test("should print the notes"): (stateRef, linePrinter) =>
      forAllF(testCaseGen):
        case TestCase(notes, expectedLines) =>
          (for
            _ <- stateRef.update(_.setLines(List.empty))
            testee = HardwareDispenserAdapter[IO](linePrinter)
            _ <- testee.dispense(notes)
            finalState <- stateRef.get
          yield finalState.lines).assertEquals(expectedLines)

  private def withLinePrinter =
    ResourceFunFixture:
      Resource.eval:
        Ref
          .of[IO, LinePrinterState](LinePrinterState.empty)
          .map: stateRef =>
            stateRef -> FakeLinePrinter(stateRef)

object HardwareDispenserAdapterSuite:
  final private case class TestCase(
      notes: Map[Denomination, Quantity],
      expectedLines: List[String],
  )

  private val testCaseGen =
    for
      size <- Gen.choose(1, 7)
      denominations <- Gen
        .containerOfN[Set, Denomination](size, denominationGen)
        .map(_.toList)
      notes <- denominations.traverse: denomination =>
        quantityGen.map(denomination -> _)
      lines = notes
        .sortBy:
          case (denomination, _) => denomination
        .map(HardwareDispenserAdapter.toLine)
    yield TestCase(notes.toMap, lines)
