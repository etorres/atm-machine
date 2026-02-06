package es.eriktorr
package atm.domain.model.types

import atm.infrastructure.TemporalGenerators.instantGen
import cash.domain.model.CashGenerators.{accountIdGen, moneyGen}
import cash.domain.model.{AccountId, Money}

import cats.implicits.*
import org.scalacheck.Gen
import org.scalacheck.cats.implicits.given

import java.time.Instant
import java.util.UUID

object AuditEntryGenerators:
  private val timestampGen =
    Gen.oneOf(TransactionState.values.toSeq)

  def auditEntryGen(
      idGen: Gen[UUID] = Gen.uuid,
      accountIdGen: Gen[AccountId] = accountIdGen,
      moneyGen: Gen[Money] = moneyGen(),
      stateGen: Gen[TransactionState] = timestampGen,
      timestampGen: Gen[Instant] = instantGen,
  ): Gen[AuditEntry] =
    (
      idGen,
      accountIdGen,
      moneyGen,
      stateGen,
      timestampGen,
    ).mapN(AuditEntry.apply)
