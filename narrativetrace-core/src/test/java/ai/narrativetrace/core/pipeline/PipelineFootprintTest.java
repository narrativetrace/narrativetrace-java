/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.footprint.Footprint;
import org.junit.jupiter.api.Test;

/**
 * Tier 0: what constructing a default pipeline costs, asserted on every commit.
 *
 * <p>INTENT: The default topology shipped a 7 MiB ring for months without anyone noticing, because
 * a throughput harness builds its pipeline once in {@code @Setup} and a per-instance cost never
 * shows up in ns/op. These are ordinary tests — milliseconds, deterministic, inside {@code check} —
 * so the next time the footprint moves, it moves in a diff.
 *
 * @see ai.narrativetrace.core.context.ContextFootprintTest for the same invariants one layer up
 */
class PipelineFootprintTest {

  @Test
  void aDefaultPipelineStaysInsideTheRetainedHeapBudget() {
    var retained = Footprint.retainedBytesPerInstance(DualPathPipeline::new, 16);

    assertThat(retained).isLessThanOrEqualTo(Footprint.DEFAULT_TOPOLOGY_BUDGET_BYTES);
  }

  @Test
  void theDefaultRingIsTheDeclaredDefaultCapacity() {
    try (var pipeline = new DualPathPipeline()) {
      var retention = (BufferedEventConsumer) pipeline.bestEffortConsumer();

      assertThat(retention.bufferCapacity()).isEqualTo(BufferedEventConsumer.DEFAULT_CAPACITY);
    }
  }

  @Test
  void constructingTheDefaultTopologyStartsNoThread() {
    long before = Footprint.liveLibraryThreads();

    try (var pipeline = new DualPathPipeline()) {
      assertThat(Footprint.liveLibraryThreads()).isEqualTo(before);
      assertThat(pipeline.retainsEvents()).isTrue();
    }
  }

  @Test
  void constructingTheBootstrappedDefaultTopologyStartsNoThread() {
    long before = Footprint.liveLibraryThreads();

    try (var pipeline = PipelineBootstrap.createDefault()) {
      assertThat(Footprint.liveLibraryThreads()).isEqualTo(before);
      assertThat(pipeline).isInstanceOf(DualPathPipeline.class);
    }
  }

  @Test
  void theDefaultTopologyRegistersNoShutdownHook() {
    try (var pipeline = new DualPathPipeline()) {
      var retention = (BufferedEventConsumer) pipeline.bestEffortConsumer();

      assertThat(retention.shutdownHook()).isNull();
    }
  }

  /**
   * The contrast that gives the previous test its meaning: the thread-starting constructor does
   * register a hook, and the JVM says so.
   */
  @Test
  void theThreadStartingConstructorRegistersItsHookWithTheJvm() {
    try (var consumer = new BufferedEventConsumer(64)) {
      assertThat(consumer.consumerAlive()).isTrue();
      assertThat(Runtime.getRuntime().removeShutdownHook(consumer.shutdownHook())).isTrue();
    }
  }

  @Test
  void closingStopsTheDrainThreadAndDeregistersTheHook() {
    var consumer = new BufferedEventConsumer(64);
    var hook = consumer.shutdownHook();

    consumer.close();

    assertThat(consumer.consumerAlive()).isFalse();
    assertThat(Runtime.getRuntime().removeShutdownHook(hook)).isFalse();
  }
}
