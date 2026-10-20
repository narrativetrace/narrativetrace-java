/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * §176 build step 4 (folds B-126): what changed when the extension moved from one pipeline per test
 * method to one per test JVM.
 *
 * <p><b>@llmNote</b> B-126, as filed, described the per-test pipeline as starting a daemon consumer
 * thread, a watchdog and a shutdown hook — but {@code PipelineBootstrap}'s default topology has
 * built its {@code BufferedEventConsumer} with {@code startConsumer=false} since 2026-08-31 (see
 * {@code ai.narrativetrace.core.pipeline.PipelineFootprintTest}), so none of the three were ever
 * actually started; {@link #theThreadAndShutdownHookCountStaysAtZeroAcrossManyTests} is a sanity
 * check, not a red-to-green proof — it reads the same (zero) before and after this change on this
 * tree. The real, measurable change this step proves is {@link
 * #theSuiteSharesOnePipelineInstanceAcrossManyTestsInOneClass}: before, {@code
 * PipelineIdentityFixture}'s five tests would have handed back five distinct context identities
 * (one {@code PipelineBootstrap} construction — a classpath scan and two {@code ServiceLoader}
 * passes — per test); now they hand back one.
 */
class PipelineOwnershipFootprintTest {

  @Test
  void theSuiteSharesOnePipelineInstanceAcrossManyTestsInOneClass() {
    PipelineIdentityFixture.contextIdentities.clear();

    runSuite(PipelineIdentityFixture.class);

    assertThat(PipelineIdentityFixture.contextIdentities)
        .hasSize(PipelineIdentityFixture.TEST_COUNT);
    assertThat(new HashSet<>(PipelineIdentityFixture.contextIdentities))
        .as("every test in the class should have been handed the same suite-owned context")
        .hasSize(1);
  }

  @Test
  void theThreadAndShutdownHookCountStaysAtZeroAcrossManyTests() {
    long before = consumerThreadCount();

    runSuite(PipelineIdentityFixture.class);

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

  private static void runSuite(Class<?> fixture) {
    var request =
        LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(fixture))
            .build();
    LauncherFactory.create().execute(request);
  }
}
