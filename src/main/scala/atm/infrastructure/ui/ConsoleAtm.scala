package es.eriktorr
package atm.infrastructure.ui

import atm.application.AtmApplicationService
import atm.application.AtmApplicationService.DispenseError
import atm.application.AtmApplicationService.DispenseError.*
import cash.domain.model.*

import cats.effect.Async
import cats.effect.std.Console
import cats.implicits.*
import cats.mtl.Handle
import squants.market.Currency

trait ConsoleAtm[F[_]]:
  def start(): F[Unit]

@SuppressWarnings(Array("org.wartremover.warts.Any"))
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
              Console[F].errorln(s"Invalid Account ID: $error") >> start()
            case Right(accountId) =>
              menu(accountId)
        yield ()

      private def menu(accountId: AccountId): F[Unit] =
        for
          _ <- Console[F].print(s"Enter amount in ${currency.code} to withdraw: ")
          maybeAmount <- Console[F].readLine.map:
            _.toIntOption
              .toRight("Expected a number")
              .flatMap(Money.Amount.either)
          _ <- maybeAmount match
            case Left(error) =>
              Console[F].errorln(s"Invalid amount: $error") >> menu(accountId)
            case Right(amount) =>
              withdraw(accountId, Money(amount, currency))
        yield ()

      private def withdraw(accountId: AccountId, money: Money) =
        Handle
          .allow[DispenseError]:
            atmApp
              .withdraw(accountId, money)
              .flatMap: _ =>
                Console[F].println("Successfully dispensed! Have a nice day.")
          .rescue:
            case InsufficientFunds =>
              Console[F].println("Operation Failed: Your account has insufficient funds.")
            case InsufficientCash(availableDenominations) if availableDenominations.nonEmpty =>
              for
                suggested = availableDenominations
                  .map(_.toString)
                  .mkString(s"${currency.symbol} ", ", ", "")
                _ <- Console[F].println("The requested amount cannot be made with current notes.")
                _ <- Console[F].println(s"Currently we can offer you: $suggested")
                _ <- Console[F].print("Try again? (y/n): ")
                _ <- Console[F].readLine.flatMap:
                  case "y" => menu(accountId)
                  case _ => Async[F].unit
              yield ()
            case InsufficientCash(availableDenominations) =>
              Console[F].println("Operation Failed: The ATM is currently out of cash.")
          .handleErrorWith: _ =>
            Console[F].errorln("An unexpected technical error occurred.")
