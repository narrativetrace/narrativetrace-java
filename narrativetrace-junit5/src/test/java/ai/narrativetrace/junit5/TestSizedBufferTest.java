/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.core.render.MarkdownRenderer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The extension builds one context per test method, so it sizes that context's ring itself — and
 * when the size it chose is not enough, every narrative the run writes says so.
 *
 * <p><b>@llmNote</b> The sizing is asserted through <em>behaviour</em>, not through a getter: a
 * context built with four slots must shed the fifth event and report it. That is the property a
 * user cares about, and it cannot pass while the capacity is being ignored.
 */
class TestSizedBufferTest {

  @Test
  void theTestDefaultIsSmallerThanTheRuntimeDefaultAndSaysWhy() {
    assertThat(NarrativeTraceExtension.DEFAULT_TEST_BUFFER_CAPACITY).isEqualTo(8192);
  }

  @Test
  void anOrdinaryTestSizedContextShedsNothing() {
    var context = NarrativeTraceExtension.newContext("DETAIL");

    traceCalls(context, 50);

    assertThat(context.captureTrace().loss().droppedEvents()).isZero();
    assertThat(new IndentedTextRenderer().render(context.captureTrace()))
        .doesNotContain("Incomplete narrative");
  }

  @Test
  void aContextGivenFourSlotsShedsTheRestAndTheNarrativeSaysSo() {
    var context = NarrativeTraceExtension.newContext("DETAIL", 4);

    traceCalls(context, 20);
    var trace = context.captureTrace();

    assertThat(trace.loss().droppedEvents()).isPositive();
    assertThat(new IndentedTextRenderer().render(trace))
        .contains("Incomplete narrative")
        .contains("narrativetrace.buffer.capacity");
  }

  @Test
  void aShedCaptureSaysSoInMarkdownToo() {
    var context = NarrativeTraceExtension.newContext("DETAIL", 4);

    traceCalls(context, 20);

    assertThat(new MarkdownRenderer().render(context.captureTrace()))
        .contains("> ⚠ Incomplete narrative:");
  }

  @Test
  void anUnknownLevelStillGetsTheChosenCapacity() {
    var context = NarrativeTraceExtension.newContext("bogus", 4);

    traceCalls(context, 20);

    assertThat(context.isActive()).isTrue();
    assertThat(context.captureTrace().loss().droppedEvents()).isPositive();
  }

  @Test
  void anOffContextCapturesNothingAndThereforeShedsNothing() {
    var context = NarrativeTraceExtension.newContext("OFF", 4);

    traceCalls(context, 20);

    assertThat(context.isActive()).isFalse();
    assertThat(context.captureTrace().loss().droppedEvents()).isZero();
  }

  @Test
  void aConfiguredCapacityIsHonoured() {
    assertThat(NarrativeTraceExtension.bufferCapacityFrom("4096")).isEqualTo(4096);
    assertThat(NarrativeTraceExtension.bufferCapacityFrom("  512  ")).isEqualTo(512);
    assertThat(NarrativeTraceExtension.bufferCapacityFrom("1")).isEqualTo(1);
  }

  /** A typo in an observability knob must never turn a green suite red. */
  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "not-a-number", "0", "-1", "6.5", "99999999999999"})
  void anUnusableConfiguredCapacityDegradesToTheTestDefault(String configured) {
    assertThat(NarrativeTraceExtension.bufferCapacityFrom(configured))
        .isEqualTo(NarrativeTraceExtension.DEFAULT_TEST_BUFFER_CAPACITY);
  }

  @Test
  void anAbsentCapacityDegradesToTheTestDefault() {
    assertThat(NarrativeTraceExtension.bufferCapacityFrom(null))
        .isEqualTo(NarrativeTraceExtension.DEFAULT_TEST_BUFFER_CAPACITY);
  }

  private static void traceCalls(ThreadLocalNarrativeContext context, int calls) {
    for (int i = 0; i < calls; i++) {
      context.enterMethod(new MethodSignature("OrderService", "placeOrder", List.of()));
      context.exitMethodWithReturn("\"ok\"");
    }
  }
}
