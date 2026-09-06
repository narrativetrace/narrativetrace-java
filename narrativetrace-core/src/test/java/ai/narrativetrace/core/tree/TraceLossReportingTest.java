/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.tree;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceLoss;
import ai.narrativetrace.core.context.NoopNarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Cross-package: what a caller can learn about a trace being incomplete, through the public API.
 */
class TraceLossReportingTest {

  private ThreadLocalNarrativeContext context;
  private ExecutorService worker;

  @BeforeEach
  void setUp() {
    context = new ThreadLocalNarrativeContext();
    worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "loss-worker"));
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    worker.shutdownNow();
    worker.awaitTermination(5, TimeUnit.SECONDS);
    context.reset();
  }

  @Test
  void anOrdinaryRunReportsNoLoss() throws Exception {
    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context.exitMethodWithReturn("ok");
    var snapshot = context.snapshot();
    worker
        .submit(
            () -> {
              try (var scope = snapshot.activate()) {
                context.enterMethod(new MethodSignature("Notifier", "notifyAsync", List.of()));
                context.exitMethodWithReturn("true");
              }
            })
        .get(5, TimeUnit.SECONDS);

    var loss = context.traceLoss();

    assertThat(loss.any()).isFalse();
    assertThat(loss).isEqualTo(TraceLoss.none());
    assertThat(context.captureTrace().roots()).hasSize(2);
  }

  @Test
  void aContextWithoutAPipelineReportsNoLoss() {
    assertThat(NoopNarrativeContext.INSTANCE.traceLoss()).isEqualTo(TraceLoss.none());
  }

  @Test
  void lossReadTwiceAroundAScenarioAttributesNothingToAQuietOne() throws Exception {
    var before = context.traceLoss();
    context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
    context.exitMethodWithReturn("ok");

    assertThat(context.traceLoss().since(before)).isEqualTo(TraceLoss.none());
  }
}
