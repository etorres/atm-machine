package es.eriktorr
package atm.infrastructure.persistence.doobie

import atm.domain.model.types.{AuditEntry, TransactionState}
import atm.repository.TransactionAuditor

import cats.effect.{Async, Resource}
import cats.implicits.*
import doobie.*
import doobie.h2.H2Transactor
import doobie.h2.implicits.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import doobie.util.ExecutionContexts

import java.time.Instant
import java.util.UUID

final class DoobieTransactionAuditor[F[_]: Async](
    transactor: Transactor[F],
) extends TransactionAuditor[F]
    with DoobieAuditProtocol:
  override def createEntry(
      entry: AuditEntry,
  ): F[Unit] =
    insert(entry)

  override def updateState(
      id: UUID,
      newState: TransactionState,
      at: Instant,
  ): F[Unit] =
    findLatestBy(id).flatMap:
      case Some(auditEntry) =>
        insert(auditEntry.copy(state = newState, timestamp = at))
      case None =>
        Async[F].raiseError(
          IllegalArgumentException(show"Transaction with id $id not found"),
        )

  def findBy(id: UUID): F[List[AuditEntry]] =
    sql"""|
          |SELECT
          |  id, account_id, money_amount, money_currency, state, timestamp
          |FROM audit_log
          |WHERE id = $id
          |""".stripMargin
      .query[AuditEntry]
      .to[List]
      .transact(transactor)

  private def findLatestBy(id: UUID) =
    sql"""|
          |SELECT
          |  id, account_id, money_amount, money_currency, state, timestamp
          |FROM audit_log
          |WHERE id = $id
          |ORDER BY timestamp DESC
          |LIMIT 1
          |""".stripMargin
      .query[AuditEntry]
      .option
      .transact(transactor)

  private def insert(entry: AuditEntry) =
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

object DoobieTransactionAuditor:
  def make[F[_]: Async](
      poolSize: Int = 2,
  ): Resource[F, TransactionAuditor[F]] =
    ExecutionContexts
      .fixedThreadPool[F](poolSize)
      .flatMap: executionContext =>
        H2Transactor
          .newH2Transactor[F](
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
            user = "sa",
            pass = "",
            connectEC = executionContext,
          )
          .evalTap: transactor =>
            (for
              _ <- sql"""|
                         |CREATE TABLE IF NOT EXISTS audit_log (
                         |  id UUID NOT NULL,
                         |  account_id VARCHAR(32) NOT NULL,
                         |  money_amount DECIMAL(19, 4) NOT NULL,
                         |  money_currency VARCHAR(3) NOT NULL,
                         |  state VARCHAR(32) NOT NULL,
                         |  timestamp TIMESTAMP NOT NULL,
                         |  PRIMARY KEY (id, state)
                         |)
                         |""".stripMargin.update.run
              _ <- sql"""|
                         |CREATE INDEX idx_audit_log_id ON audit_log (id)
                         |""".stripMargin.update.run.attempt.void
            yield ()).transact(transactor)
          .map(DoobieTransactionAuditor(_))
