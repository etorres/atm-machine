package es.eriktorr
package atm.repository

import atm.domain.model.types.{AuditEntry, TransactionState}
import cash.domain.model.{AccountId, Money}

import cats.effect.{Async, Resource}
import cats.implicits.*
import doobie.*
import doobie.h2.H2Transactor
import doobie.h2.implicits.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import doobie.util.ExecutionContexts
import squants.market.{defaultMoneyContext, Currency, MoneyContext}

import java.util.UUID

trait TransactionAuditor[F[_]]:
  def createEntry(
      entry: AuditEntry,
  ): F[Unit]

  def updateState(
      id: UUID,
      newState: TransactionState,
  ): F[Unit]

object TransactionAuditor:
  def make[F[_]: Async]: Resource[F, TransactionAuditor[F]] =
    ExecutionContexts
      .fixedThreadPool[F](2)
      .flatMap: executionContext =>
        H2Transactor
          .newH2Transactor[F](
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
            user = "sa",
            pass = "",
            connectEC = executionContext,
          )
          .evalTap: transactor =>
            sql"""|
                  |CREATE TABLE audit_log (
                  |  id UUID PRIMARY KEY,
                  |  account_id VARCHAR(32) NOT NULL,
                  |  money DECIMAL(19, 4) NOT NULL,
                  |  state VARCHAR(32) NOT NULL,
                  |  timestamp TIMESTAMP NOT NULL
                  |)
                  |""".stripMargin.update.run.transact(transactor)
          .map: transactor =>
            new TransactionAuditor[F]:
              override def createEntry(
                  entry: AuditEntry,
              ): F[Unit] =
                insert(entry)

              override def updateState(
                  id: UUID,
                  newState: TransactionState,
              ): F[Unit] =
                findBy(id).flatMap:
                  case Some(auditEntry) => insert(auditEntry)
                  case None =>
                    Async[F].raiseError(
                      IllegalArgumentException(show"Transaction with id $id not found"),
                    )

              private def findBy(
                  id: UUID,
              ) =
                sql"""|
                      |SELECT
                      |  id,
                      |  account_id,
                      |  money,
                      |  state,
                      |  timestamp
                      |FROM audit_log
                      |WHERE id = $id
                      | AND state = ${TransactionState.Started}
                      |""".stripMargin
                  .query[AuditEntry]
                  .option
                  .transact(transactor)

              private def insert(
                  entry: AuditEntry,
              ) =
                sql"""|
                      |INSERT INTO audit_log VALUES (
                      |  ${entry.id},
                      |  ${entry.accountId},
                      |  ${entry.money},
                      |  ${entry.state},
                      |  ${entry.timestamp}
                      |)
                    """.stripMargin.update.run
                  .transact(transactor)
                  .ensure(RuntimeException("Insert failed: affected rows != 1"))(_ == 1)
                  .void

  given Meta[AccountId] =
    Meta[String].imap(AccountId.applyUnsafe)(identity)

  given Meta[Money.Amount] =
    Meta[Int].imap(Money.Amount.applyUnsafe)(identity)

  given MoneyContext = defaultMoneyContext

  given Meta[Currency] =
    Meta[String].tiemap(code => Currency(code).toEither.leftMap(_.getMessage))(_.code)

  given Read[Money] =
    Read[(Money.Amount, Currency)].map: (amount, currency) =>
      Money(amount, currency)

  given Write[Money] =
    Write[(Money.Amount, Currency)].contramap: money =>
      (money.amount, money.currency)

  given Get[TransactionState] =
    Get.deriveEnumString[TransactionState]

  given Put[TransactionState] =
    Put.deriveEnumString[TransactionState]
