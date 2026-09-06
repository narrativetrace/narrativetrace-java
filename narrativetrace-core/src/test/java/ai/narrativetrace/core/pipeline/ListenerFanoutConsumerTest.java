/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TestSpanContext;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.spi.TraceEventListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ListenerFanoutConsumerTest {

  private BufferedEventConsumer retention;

  @AfterEach
  void tearDown() {
    if (retention != null) {
      retention.close();
    }
  }

  @Test
  void feedsRetentionAndEveryListener() {
    var firstSeen = new ArrayList<TraceEvent>();
    var secondSeen = new ArrayList<TraceEvent>();
    var fanout = fanoutOf(firstSeen::add, secondSeen::add);
    var event = enterEvent();

    fanout.accept(event);
    fanout.flush();

    assertThat(fanout.events()).containsExactly(event);
    assertThat(firstSeen).containsExactly(event);
    assertThat(secondSeen).containsExactly(event);
  }

  @Test
  void oneThrowingListenerIsDisabledWhileTheOtherKeepsReceiving() {
    var healthySeen = new ArrayList<TraceEvent>();
    var brokenCalls = new int[1];
    var fanout =
        fanoutOf(
            event -> {
              brokenCalls[0]++;
              throw new IllegalStateException("broken");
            },
            healthySeen::add);

    fanout.accept(enterEvent());
    fanout.accept(enterEvent());
    fanout.flush();

    assertThat(brokenCalls[0]).isOne();
    assertThat(healthySeen).hasSize(2);
    assertThat(fanout.events()).hasSize(2);
  }

  @Test
  void storeOperationsPassStraightThroughToRetention() {
    var fanout = fanoutOf(event -> {});
    fanout.accept(enterEvent());
    fanout.flush();
    assertThat(fanout.events()).hasSize(1);

    fanout.clear();

    assertThat(fanout.events()).isEmpty();
  }

  @Test
  void removeSpansDelegatesToRetention() {
    var fanout = fanoutOf(event -> {});
    var event = enterEvent();
    fanout.accept(event);
    fanout.flush();

    fanout.removeSpans(Set.of(event.spanContext().spanId()));

    assertThat(fanout.events()).isEmpty();
  }

  @Test
  void removeSpansLeavesOtherTracesUntouched() {
    var fanout = fanoutOf(event -> {});
    var kept = enterEvent();
    var dropped = enterEvent();
    fanout.accept(kept);
    fanout.accept(dropped);
    fanout.flush();

    fanout.removeSpans(Set.of(dropped.spanContext().spanId()));

    assertThat(fanout.events()).containsExactly(kept);
  }

  @Test
  void closeIsSafeAndClosesTheUnderlyingRetention() {
    var fanout = fanoutOf(event -> {});

    assertThatNoException().isThrownBy(fanout::close);
  }

  @Test
  void closeToleratesRetentionThatIsNotCloseable() {
    var fanout = new ListenerFanoutConsumer(new NoopRetention(), List.of(event -> {}));

    assertThatNoException().isThrownBy(fanout::close);
  }

  @Test
  void rejectsMissingCollaborators() {
    retention = new BufferedEventConsumer(8, false);
    List<TraceEventListener> none = List.of();

    assertThatThrownBy(() -> new ListenerFanoutConsumer(null, List.of(event -> {})))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retention");
    assertThatThrownBy(() -> new ListenerFanoutConsumer(retention, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("listeners");
    assertThatThrownBy(() -> new ListenerFanoutConsumer(retention, none))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("listeners");
  }

  private ListenerFanoutConsumer fanoutOf(TraceEventListener... listeners) {
    retention = new BufferedEventConsumer(64, false);
    return new ListenerFanoutConsumer(retention, List.of(listeners));
  }

  private static TraceEvent.EnterEvent enterEvent() {
    return new TraceEvent.EnterEvent(
        TestSpanContext.create(), System.nanoTime(), new MethodSignature("Foo", "bar", List.of()));
  }

  /** Retention with no resources to release, proving close() does not assume AutoCloseable. */
  private static final class NoopRetention implements RetainingConsumer {
    @Override
    public void accept(TraceEvent event) {
      // no-op
    }

    @Override
    public void flush() {
      // no-op
    }

    @Override
    public List<TraceEvent> events() {
      return List.of();
    }

    @Override
    public void clear() {
      // no-op
    }

    @Override
    public void removeSpans(Set<SpanId> spanIds) {
      // no-op
    }
  }
}
