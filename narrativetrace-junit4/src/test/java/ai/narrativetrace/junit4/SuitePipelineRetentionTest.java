/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import ai.narrativetrace.core.context.NarrativeContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * B-150: a {@code @ClassRule} field is static and its class never unloads, so whatever the class
 * rule holds when the class ends is retained for the life of the test JVM. One pipeline per class
 * therefore retains one event ring per class — ~1.75 MB each, which exhausts a 512 MB test heap at
 * a few hundred test classes.
 *
 * <p>The measurable the fix is pinned on is the live pipeline count, not a heap reading: a context
 * instance stands one-for-one for the pipeline it was handed (each is built together, once), and
 * identity is deterministic where a retained-bytes reading on a shared container is not.
 */
class SuitePipelineRetentionTest {

  /**
   * Enough classes to tell "one per JVM" from "one per class", small enough that the <em>red</em>
   * run still reports an assertion rather than an {@code OutOfMemoryError}: 400 classes × 1.75 MB
   * is the heap the bug exhausts, and a dead test JVM says nothing a reader can act on.
   */
  private static final int CLASS_COUNT = 64;

  @BeforeEach
  void resetGlobal() {
    NarrativeTraceClassRule.resetGlobalAccumulator();
  }

  @Test
  void manyClassRulesShareOneContextForTheWholeTestJvm() throws Throwable {
    // Each rule stands in for one test class's static @ClassRule field: still reachable, for good,
    // after the class it belongs to has finished.
    var staticFields = new ArrayList<NarrativeTraceClassRule>();
    Set<NarrativeContext> live = Collections.newSetFromMap(new IdentityHashMap<>());

    for (int i = 0; i < CLASS_COUNT; i++) {
      var classRule = new NarrativeTraceClassRule();
      staticFields.add(classRule);
      classRule
          .apply(recording(classRule, live), Description.createSuiteDescription("Fixture" + i))
          .evaluate();
    }

    assertThat(staticFields).hasSize(CLASS_COUNT);
    assertThat(live)
        .as("every class rule in one test JVM should share the one suite-owned context")
        .hasSize(1);
  }

  @Test
  void twoClassesWritePerTestFilesHoldingOnlyTheirOwnCalls(@TempDir Path tempDir) throws Exception {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      LauncherFactory.create()
          .execute(
              LauncherDiscoveryRequestBuilder.request()
                  .selectors(
                      selectClass(SharedPipelineFixtureOne.class),
                      selectClass(SharedPipelineFixtureTwo.class))
                  .build());
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }

    assertThat(traceFile(tempDir, "SharedPipelineFixtureOne", "warehouse_packs_parcel"))
        .contains("WarehouseService.packParcel")
        .doesNotContain("shipParcel")
        .doesNotContain("CourierService");
    assertThat(traceFile(tempDir, "SharedPipelineFixtureOne", "warehouse_ships_parcel"))
        .contains("WarehouseService.shipParcel")
        .doesNotContain("packParcel")
        .doesNotContain("CourierService");
    assertThat(traceFile(tempDir, "SharedPipelineFixtureTwo", "courier_collects_parcel"))
        .contains("CourierService.collectParcel")
        .doesNotContain("WarehouseService");
  }

  private static String traceFile(Path outputDir, String fixture, String scenario)
      throws java.io.IOException {
    return Files.readString(outputDir.resolve("traces/" + fixture + "/" + scenario + ".md"));
  }

  /** A class statement that records which context the rule handed this class. */
  private static Statement recording(
      NarrativeTraceClassRule classRule, Set<NarrativeContext> live) {
    return new Statement() {
      @Override
      public void evaluate() {
        live.add(classRule.context());
      }
    };
  }
}
