package es.eriktorr
package atm.infrastructure

import atm.domain.PhysicalDispenser
import cash.domain.model.{Denomination, Quantity}

import cats.effect.kernel.Sync
import cats.implicits.*

final class HardwareDispenserAdapter[F[_]: Sync](
    linePrinter: LinePrinter[F],
) extends PhysicalDispenser[F]:
  override def dispense(
      notes: Map[Denomination, Quantity],
  ): F[Unit] =
    notes.toList
      .sortBy:
        case (denomination, _) => denomination
      .reverse
      .map(HardwareDispenserAdapter.toLine)
      .traverse(linePrinter.print)
      .void

object HardwareDispenserAdapter:
  val toLine: ((Denomination, Quantity)) => String =
    (denomination, quantity) =>
      show"$quantity bill${if quantity > 1 then "s" else ""} of $denomination"
