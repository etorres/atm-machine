package es.eriktorr
package atm.infrastructure.persistence.doobie

import atm.domain.model.types.AuditEntryGenerators.auditEntryGen
import atm.domain.model.types.{AuditEntry, TransactionState}
import atm.infrastructure.persistence.doobie.DoobieTransactionAuditorSuite.{testCaseGen, TestCase}
import test.gen.TemporalGenerators.withinInstantRange
import test.utils.ScalaCheckShuffler.shufflingGen

import cats.collections.Range
import cats.effect.IO
import cats.implicits.*
import munit.{AnyFixture, CatsEffectSuite, ScalaCheckEffectSuite}
import org.scalacheck.cats.implicits.given
import org.scalacheck.effect.PropF.forAllF
import org.scalacheck.{Gen, Test}

import java.time.Instant

final class DoobieTransactionAuditorSuite extends CatsEffectSuite with ScalaCheckEffectSuite:
  test("should track transactions with doobie"):
    forAllF(testCaseGen):
      case TestCase(initialEntry, otherEntries, expected) =>
        (for
          transactionAuditor <- IO(transactionAuditorFixture())
          _ <- transactionAuditor.createEntry(initialEntry)
          _ <- otherEntries.traverse_ : auditEntry =>
            transactionAuditor.updateState(
              auditEntry.id,
              auditEntry.state,
              auditEntry.timestamp,
            )
          obtained <- transactionAuditor.findBy(initialEntry.id)
        yield obtained.sortBy(_.timestamp))
          .assertEquals(expected.sortBy(_.timestamp))

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  private lazy val transactionAuditorFixture =
    ResourceSuiteLocalFixture(
      "test-transaction-auditor",
      DoobieTransactionAuditor
        .make[IO](2)
        .map(_.asInstanceOf[DoobieTransactionAuditor[IO]]),
    )

  override def munitFixtures: Seq[AnyFixture[?]] =
    List(transactionAuditorFixture)

  override def scalaCheckTestParameters: Test.Parameters =
    super.scalaCheckTestParameters.withMinSuccessfulTests(1)

object DoobieTransactionAuditorSuite:
  final private case class TestCase(
      initialEntry: AuditEntry,
      otherEntries: List[AuditEntry],
      expected: List[AuditEntry],
  )

  private val testCaseGen =
    for
      initialEntry <- auditEntryGen(stateGen = TransactionState.Started)
      steps <- Gen.choose(1, TransactionState.values.length - 1)
      possibleStates = TransactionState.values.toSet - initialEntry.state
      shuffler <- shufflingGen
      states = shuffler.shuffle(possibleStates.toList).take(steps)
      timestampRange =
        Range(
          initialEntry.timestamp.plusSeconds(1),
          initialEntry.timestamp.plusSeconds(60),
        )
      timestamps <-
        Gen
          .containerOfN[Set, Instant](
            steps,
            withinInstantRange(timestampRange),
          )
          .map(_.toList)
      otherEntries <- states
        .zip(timestamps)
        .traverse: (state, timestamp) =>
          auditEntryGen(
            idGen = initialEntry.id,
            accountIdGen = initialEntry.accountId,
            moneyGen = initialEntry.money,
            stateGen = state,
            timestampGen = timestamp,
          )
        .map(_.sortBy(_.timestamp))
    yield TestCase(
      initialEntry,
      otherEntries,
      initialEntry :: otherEntries,
    )
