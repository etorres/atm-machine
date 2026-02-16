package es.eriktorr
package atm.domain.model.types

import atm.domain.model.AccountId
import atm.domain.model.AtmGenerators.accountIdGen
import cash.domain.model.CashGenerators.moneyGen
import cash.domain.model.Money
import test.gen.TemporalGenerators.instantGen

import cats.implicits.*
import org.scalacheck.Gen
import org.scalacheck.cats.implicits.given

import java.time.Instant
import java.util.UUID

object AuditEntryGenerators:
  val transactionIdGen: Gen[TransactionId] =
    Gen.uuid.map(TransactionId.unsafeFrom)

  private val transactionStateGen =
    Gen.oneOf(TransactionState.values.toSeq)

  def auditEntryGen(
      idGen: Gen[TransactionId] = transactionIdGen,
      accountIdGen: Gen[AccountId] = accountIdGen,
      moneyGen: Gen[Money] = moneyGen(),
      stateGen: Gen[TransactionState] = transactionStateGen,
      timestampGen: Gen[Instant] = instantGen,
  ): Gen[AuditEntry] =
    (
      idGen,
      accountIdGen,
      moneyGen,
      stateGen,
      timestampGen,
    ).mapN(AuditEntry.apply)
