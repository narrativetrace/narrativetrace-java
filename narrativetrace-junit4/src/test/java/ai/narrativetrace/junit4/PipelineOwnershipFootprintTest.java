/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * §176 build step 4 (folds B-126) — JUnit 4 half, mirroring {@code
 * ai.narrativetrace.junit5.PipelineOwnershipFootprintTest}: the class rule now owns one pipeline
 * for the whole class instead of the per-test rule building a fresh one per test method. See that
 * class's javadoc for why the thread/shutdown-hook count was already zero before this change —
 * {@code PipelineBootstrap}'s default topology starts no consumer thread — and why the pipeline
 * <em>instance</em> count is the real, measurable before/after here.
 */
class PipelineOwnershipFootprintTest {

  @Test
  void theClassSharesOnePipelineInstanceAcrossManyTests() {
    PipelineIdentityJunit4Fixture.contextIdentities.clear();

    runNested(PipelineIdentityJunit4Fixture.class);

    assertThat(PipelineIdentityJunit4Fixture.contextIdentities)
        .hasSize(PipelineIdentityJunit4Fixture.TEST_COUNT);
    assertThat(new HashSet<>(PipelineIdentityJunit4Fixture.contextIdentities))
        .as("every test in the class should have been handed the same class-owned context")
        .hasSize(1);
  }

  @Test
  void theThreadAndShutdownHookCountStaysAtZeroAcrossManyTests() {
    long before = consumerThreadCount();

    runNested(PipelineIdentityJunit4Fixture.class);

    assertThat(consumerThreadCount())
        .as("the default topology starts no narrative-trace-consumer thread at all")
        .isEqualTo(before)
        .isZero();
  }

  private static long consumerThreadCount() {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(t -> t.getName().equals("narrative-trace-consumer"))
        .count();
  }

  private static void runNested(Class<?> fixture) {
    var request =
        LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(fixture))
            .build();
    LauncherFactory.create().execute(request);
  }
}
