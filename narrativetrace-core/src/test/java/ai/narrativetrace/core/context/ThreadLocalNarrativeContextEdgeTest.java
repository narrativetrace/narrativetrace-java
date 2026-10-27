/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.api.event.ConcurrencyKind;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Edges of {@link ThreadLocalNarrativeContext} that the suite reaches through no other test: the
 * two capture-flag accessors, the exit of a span this context never created, the async group key a
 * parentless snapshot falls back to, and the two early returns on the capture path.
 *
 * <p>INTENT: Each of these is a line a behavioural change passed through unnoticed — an accessor
 * with no coverage at all, or a guard whose removal no assertion felt. Pinning them is what makes
 * the next refactor of the capture path tell the truth about what it changed.
 */
class ThreadLocalNarrativeContextEdgeTest {

  private static final MethodSignature CALL =
      new MethodSignature("OrderService", "placeOrder", List.of());

  private final NarrativeTraceConfig config = new NarrativeTraceConfig(TracingLevel.DETAIL);
  private final DualPathPipeline pipeline = new DualPathPipeline();
  private final ThreadLocalNarrativeContext context =
      new ThreadLocalNarrativeContext(config, pipeline);

  @AfterEach
  void clearTheThread() {
    context.reset();
  }

  @Test
  void instanceIdCaptureFollowsTheConfiguredFlag() {
    assertThat(context.capturesInstanceIds())
        .as("off by default — an identity hash is only worth its bytes when asked for")
        .isFalse();

    config.setCaptureInstanceIds(true);

    assertThat(context.capturesInstanceIds()).isTrue();
  }

  @Test
  void sourceLocationCaptureFollowsTheConfiguredFlag() {
    assertThat(context.capturesSourceLocation()).isFalse();

    config.setCaptureSourceLocation(true);

    assertThat(context.capturesSourceLocation()).isTrue();
  }

  @Test
  void parameterValueCaptureIsOnOnlyAtDetail() {
    assertThat(context.capturesParameterValues()).isTrue();

    config.setLevel(TracingLevel.NARRATIVE);

    assertThat(context.capturesParameterValues()).isFalse();
  }

  /**
   * Asserted on the pipeline, not on {@code events()}: the thread-scoped view filters an event
   * whose span context is {@code null} out again, so it would report the same thing either way.
   */
  @Test
  void anExitForASpanThisContextNeverCreatedPublishesNothing() {
    context.enterMethod(CALL);

    context.exitMethodWithReturn("v", SpanId.generate());

    pipeline.flush();
    assertThat(pipeline.events())
        .as("an unknown span has no context to exit from; inventing one would forge a node")
        .hasSize(1)
        .allMatch(TraceEvent.EnterEvent.class::isInstance);
  }

  @Test
  void anExitForASpanThatIsAlreadyGoneIsRefusedRatherThanPublishedContextless() {
    var span = context.enterMethod(CALL);
    context.exitMethodWithReturn("first", span);
    context.reset();

    context.exitMethodWithReturn("second", span);

    pipeline.flush();
    assertThat(pipeline.events())
        .as("reset() took the span context with it; there is nothing left to exit")
        .isEmpty();
  }

  @Test
  void aSnapshotWithNoParentSpanGroupsItsAsyncWorkByTraceId() throws Exception {
    var traceId = context.traceId();
    var snapshot = context.snapshot();
    var tagged = new AtomicReference<TraceEvent.EnterEvent>();

    var worker =
        new Thread(
            () -> {
              try (var scope = snapshot.activate()) {
                context.enterMethod(CALL);
                tagged.set((TraceEvent.EnterEvent) context.events().get(0));
              }
            },
            "parentless-snapshot-worker");
    worker.start();
    worker.join();

    assertThat(tagged.get().concurrency().groupId())
        .as("with no parent span to group under, async work groups per trace")
        .isEqualTo("async-" + traceId);
    assertThat(tagged.get().concurrency().kind()).isEqualTo(ConcurrencyKind.ASYNC);
  }

  @Test
  void aCaptureTakenWhileTracingIsOffIsEmptyAndClaimsNoLoss() {
    context.enterMethod(CALL);
    context.exitMethodWithReturn("v");

    config.setLevel(TracingLevel.OFF);
    var tree = context.captureTrace();

    assertThat(tree.roots()).isEmpty();
    assertThat(tree.loss().refusedSpans()).isZero();
    assertThat(tree.loss().droppedEvents()).isZero();
  }

  @Test
  void collectingOnAThreadThatNeverTracedCountsNoLoss() {
    var tree = context.collectLocalTrace();

    assertThat(tree.roots()).isEmpty();
    assertThat(context.traceLoss().refusedScopes())
        .as("a scope with no spans of its own cannot have lost one")
        .isZero();
    assertThat(context.traceLoss().refusedSpans()).isZero();
  }
}
