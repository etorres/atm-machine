package es.eriktorr
package atm.infrastructure.ui

import atm.application.AtmApplicationService
import atm.application.AtmApplicationService.DispenseError
import atm.application.AtmApplicationService.DispenseError.*
import atm.domain.model.{AccountId, Receipt}
import cash.domain.model.*

import cats.effect.Async
import cats.effect.std.Console
import cats.implicits.*
import cats.mtl.Handle
import squants.market.Currency
import java.time.format.DateTimeFormatter

trait ConsoleAtm[F[_]]:
  def start(): F[Unit]

object ConsoleAtm:
  def apply[F[_]: {Async, Console}](
      atmApp: AtmApplicationService[F],
      currency: Currency,
  ): ConsoleAtm[F] =
    new ConsoleAtm[F]:
      override def start(): F[Unit] =
        for
          _ <- Console[F].println[String]("=== Welcome to the Trusted-ATM \uD83C\uDD2F ===")
          _ <- Console[F].print("Please enter your Account ID: ")
          maybeAccountId <- Console[F].readLine
            .map(AccountId.either)
          _ <- maybeAccountId match
            case Left(error) =>
              Console[F].errorln(show"Invalid Account ID: $error") >> start()
            case Right(accountId) =>
              menu(accountId)
        yield ()

      private def menu(accountId: AccountId): F[Unit] =
        for
          _ <- Console[F].print(show"Enter amount in ${currency.code} to withdraw: ")
          maybeAmount <- Console[F].readLine.map:
            _.toIntOption
              .toRight("Expected a number")
              .flatMap(Money.Amount.either)
          _ <- maybeAmount match
            case Left(error) =>
              Console[F].errorln(show"Invalid amount: $error") >> menu(accountId)
            case Right(amount) =>
              withdraw(accountId, Money(amount, currency))
        yield ()

      private def withdraw(accountId: AccountId, money: Money) =
        Handle
          .allow[DispenseError]:
            atmApp
              .withdraw(accountId, money)
              .flatMap(printReceipt)
          .rescue:
            case InsufficientFunds =>
              Console[F].println("Operation Failed: Your account has insufficient funds.")
            case InsufficientCash(availableDenominations) if availableDenominations.nonEmpty =>
              for
                suggested = availableDenominations
                  .map(_.toString)
                  .mkString(show"${currency.symbol} ", ", ", "")
                _ <- Console[F].println("The requested amount cannot be made with current notes.")
                _ <- Console[F].println(show"Currently we can offer you: $suggested")
                _ <- retry(accountId)
              yield ()
            case InsufficientCash(_) =>
              Console[F].println("Operation Failed: The ATM is currently out of cash.")
            case RefundedError =>
              Console[F].println("Operation Failed: An internal error occurred.") >>
                retry(accountId)
          .handleErrorWith: _ =>
            Console[F].errorln("An unexpected technical error occurred.")

      private def retry(accountId: AccountId) =
        for
          _ <- Console[F].print("Try again? (y/n): ")
          _ <- Console[F].readLine.flatMap:
            case "y" => menu(accountId)
            case _ => Async[F].unit
        yield ()

      private def printReceipt(receipt: Receipt) =
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
        val border = "========================================"
        val output =
          show"""
                |$border
                |       TRANSACTION RECEIPT
                | Terminal: ${receipt.terminalId}
                |$border
                | Date:    ${receipt.timestamp.format(formatter)}
                | Account: ${receipt.accountId.masked}
                | Tx ID:   ${receipt.transactionId.value}
                | Type:    ${receipt.operationType}
                |
                | AMOUNT DISPENSED: ${receipt.money.currency.symbol} ${receipt.money.amount}
                |$border
                | Thank you for banking with us.
                |$border
                |""".stripMargin
        Console[F].println(output)
