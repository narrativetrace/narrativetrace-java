/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.narrativetrace.api.event.MethodSignature;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The B-150 lesson, held for TestNG: one pipeline per test JVM, never one per test or per class,
 * and each test a request-scoped slice of it whose events leave the store when the test ends.
 */
class SharedPipelineTest {

  /**
   * The owner's measurable (2026-10-23): a suite of 200 trivial tests allocates one pipeline and
   * leaves its store empty. The construction count is JVM-wide — every other test class in this JVM
   * has run tests too — so "1" means one for the whole JVM, not one for this run.
   */
  @Test
  void twoHundredTestsBuildOnePipelineAndLeaveTheStoreEmpty(@TempDir Path outputDir)
      throws Exception {
    ManyInvocationsFixture.RAN.set(0);

    TestNgRuns.run(outputDir, ManyInvocationsFixture.class);

    assertThat(ManyInvocationsFixture.RAN).hasValue(ManyInvocationsFixture.COUNT);
    assertThat(SharedPipeline.constructions())
        .as("pipelines constructed in this JVM after %d tests", ManyInvocationsFixture.COUNT)
        .isEqualTo(1);
    assertThat(storedEvents())
        .as("events left in the shared store after %d tests", ManyInvocationsFixture.COUNT)
        .isEmpty();
  }

  @Test
  void everyTestIsHandedTheSameContext(@TempDir Path outputDir) throws Exception {
    ContextIdentityFixture.IDENTITIES.clear();

    TestNgRuns.run(outputDir, ContextIdentityFixture.class);

    assertThat(ContextIdentityFixture.IDENTITIES).hasSize(3);
    assertThat(new HashSet<>(ContextIdentityFixture.IDENTITIES)).hasSize(1);
  }

  @Test
  void twoClassesWriteFilesHoldingOnlyTheirOwnCalls(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(outputDir, WarehouseFixture.class, CourierFixture.class);

    assertThat(TestNgRuns.trace(outputDir, "WarehouseFixture", "warehouse_packs_parcel"))
        .contains("WarehouseService.packParcel")
        .doesNotContain("shipParcel")
        .doesNotContain("CourierService");
    assertThat(TestNgRuns.trace(outputDir, "WarehouseFixture", "warehouse_ships_parcel"))
        .contains("WarehouseService.shipParcel")
        .doesNotContain("packParcel")
        .doesNotContain("CourierService");
    assertThat(TestNgRuns.trace(outputDir, "CourierFixture", "courier_collects_parcel"))
        .contains("CourierService.collectParcel")
        .doesNotContain("WarehouseService");
  }

  @Test
  void parallelMethodsCaptureOnlyTheirOwnCalls(@TempDir Path outputDir) throws Exception {
    ParallelFixture.allInside = new CountDownLatch(ParallelFixture.TESTS);

    var run =
        TestNgRuns.runParallelMethods(outputDir, ParallelFixture.TESTS, ParallelFixture.class);

    assertThat(run.failed()).as(run.out()).isFalse();
    for (var name : List.of("Alpha", "Bravo", "Charlie", "Delta")) {
      var others =
          List.of("Alpha", "Bravo", "Charlie", "Delta").stream()
              .filter(other -> !other.equals(name))
              .map(other -> other + "Service")
              .toList();
      assertThat(TestNgRuns.trace(outputDir, "ParallelFixture", name.toLowerCase() + "_reserves"))
          .contains(name + "Service.reserve")
          .doesNotContain(others);
    }
    assertThat(storedEvents()).isEmpty();
  }

  /**
   * The shutdown hook can fire while something still holds the context, and a harness may close
   * twice: {@code close()} is idempotent and a publish into a closed pipeline is dropped, never
   * thrown. Exercised on a private instance, since closing the shared one would end this JVM's
   * tracing.
   */
  @Test
  void closingTwiceIsSafeAndAPublishAfterwardsIsDropped() {
    var own = new SharedPipeline(SharedPipeline.DEFAULT_TEST_BUFFER_CAPACITY);

    own.close();
    own.close();

    assertThatCode(
            () -> {
              own.context().enterMethod(new MethodSignature("Late", "call", List.of()));
              own.context().exitMethodWithReturn("ignored");
            })
        .doesNotThrowAnyException();
  }

  /** What the shared store holds once the ring is drained into it — a stuck event cannot hide. */
  private static java.util.List<ai.narrativetrace.api.event.TraceEvent> storedEvents() {
    var pipeline = SharedPipeline.shared().pipeline();
    pipeline.flush();
    return pipeline.events();
  }
}
