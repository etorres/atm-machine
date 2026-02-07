package es.eriktorr
package atm.infrastructure.persistence.file

import atm.infrastructure.config.SystemSnapshotGenerators.systemSnapshotGen

import cats.effect.IO
import fs2.io.file.Files
import munit.{AnyFixture, CatsEffectSuite, ScalaCheckEffectSuite}
import org.scalacheck.Test
import org.scalacheck.effect.PropF.forAllF

final class FileStateStoreSuite extends CatsEffectSuite with ScalaCheckEffectSuite:
  test("should write and load a system snapshot"):
    forAllF(systemSnapshotGen()): systemSnapshot =>
      (for
        path <- IO.delay(tempFileFixture())
        testee = FileStateStore[IO](path.toNioPath)
        _ <- testee.dump(systemSnapshot)
        obtained <- testee.load()
      yield obtained).assertEquals(systemSnapshot)

  override def munitFixtures: Seq[AnyFixture[?]] = List(tempFileFixture)

  private lazy val tempFileFixture =
    ResourceSuiteLocalFixture(
      "temporary-file",
      Files[IO].tempFile(None, "atm-test-", ".json", None),
    )

  override def scalaCheckTestParameters: Test.Parameters =
    super.scalaCheckTestParameters.withMinSuccessfulTests(1)
