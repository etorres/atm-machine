package es.eriktorr
package atm

import atm.Atm.TellingError
import cash.Cash.{Availability, Denomination, Quantity}
import cash.CashDispenser.DispensingError
import cash.{Cash, CashDispenser, CashPrinter, CashRepository}

import cats.effect.Async
import cats.effect.implicits.genTemporalOps
import cats.effect.std.AtomicCell
import cats.implicits.*
import cats.mtl.implicits.given
import cats.mtl.{Handle, Raise}

import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.control.NoStackTrace

trait Atm[F[_]]:
  def withdraw(cash: Cash)(using Raise[F, TellingError]): F[Unit]

object Atm:
  def impl[F[_]: Async](
      atomicRepository: AtomicCell[F, CashRepository[F]],
      dispenser: CashDispenser[F],
      printer: CashPrinter[F],
      timeout: Duration = 2.seconds,
  ): Atm[F] =
    new Atm[F]:
      override def withdraw(cash: Cash)(using Raise[F, TellingError]): F[Unit] =
        atomicRepository.get
          .flatMap: repository =>
            for
              availability <- repository.availabilityIn(cash.currency)
              amounts <- makeAmounts(cash.amount, availability, dispenser)
              _ <- repository.remove(cash.currency, amounts)
              _ <- print(amounts, printer)
            yield ()
          .timeout(timeout)

  private def makeAmounts[F[_]: Async](
      amount: Quantity,
      availability: Map[Denomination, Availability],
      dispenser: CashDispenser[F],
  )(using Raise[F, TellingError]) =
    Handle
      .allow[DispensingError]:
        dispenser.minimumUnits(amount, availability)
      .rescue:
        case DispensingError.NotSolved =>
          TellingError.OutOfMoney.raise[F, Map[Denomination, Quantity]]

  private def print[F[_]: Async](
      amounts: Map[Denomination, Quantity],
      printer: CashPrinter[F],
  ) =
    amounts.toList
      .sortBy:
        case (denomination, _) => denomination
      .reverse
      .map(toLine)
      .traverse(printer.print)

  val toLine: ((Denomination, Quantity)) => String =
    (denomination, quantity) =>
      show"$quantity bill${if quantity > 1 then "s" else ""} of $denomination"

  enum TellingError(val message: String) extends RuntimeException(message) with NoStackTrace:
    case OutOfMoney
        extends TellingError(
          "This machine does not have enough money, please go to the nearest ATM",
        )
