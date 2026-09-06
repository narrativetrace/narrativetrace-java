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

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TestSpanContext;
import ai.narrativetrace.api.event.TraceEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DualPathPipelineTest {

  private DualPathPipeline pipeline;
  private BufferedEventConsumer buffered;

  @AfterEach
  void tearDown() {
    if (pipeline != null) {
      pipeline.close();
    }
  }

  @Test
  void synchronousListenerCalledOnPublish() {
    var received = new ArrayList<TraceEvent>();
    pipeline = new DualPathPipeline(received::add);
    var event = enterEvent("Foo", "bar");

    pipeline.publish(event);

    assertThat(received).containsExactly(event);
  }

  @Test
  void throwingSynchronousListenerDoesNotPropagateAndStillFeedsBestEffortPath() {
    buffered = new BufferedEventConsumer(4, false);
    pipeline =
        new DualPathPipeline(
            event -> {
              throw new IllegalStateException("listener blew up");
            },
            buffered);
    var event = enterEvent("Foo", "bar");

    assertThatNoException().isThrownBy(() -> pipeline.publish(event));

    pipeline.flush();
    assertThat(pipeline.events()).containsExactly(event);
  }

  @Test
  void throwingBestEffortConsumerDoesNotPropagate() {
    var received = new ArrayList<TraceEvent>();
    Consumer<TraceEvent> failing =
        event -> {
          throw new IllegalStateException("best-effort path blew up");
        };
    pipeline = new DualPathPipeline(received::add, failing);
    var event = enterEvent("Foo", "bar");

    assertThatNoException().isThrownBy(() -> pipeline.publish(event));

    assertThat(received).containsExactly(event);
  }

  @Test
  void synchronousListenerRunsOnCallingThread() {
    var listenerThread = new Thread[1];
    pipeline = new DualPathPipeline(event -> listenerThread[0] = Thread.currentThread());

    pipeline.publish(enterEvent("Foo", "bar"));

    assertThat(listenerThread[0]).isEqualTo(Thread.currentThread());
  }

  @Test
  void theDefaultTopologyRetainsIntoA65536SlotRing() {
    pipeline = new DualPathPipeline();

    assertThat(((BufferedEventConsumer) pipeline.bestEffortConsumer()).bufferCapacity())
        .isEqualTo(65_536);
  }

  @Test
  void bestEffortConsumerReceivesEvent() throws InterruptedException {
    buffered = new BufferedEventConsumer(4);
    pipeline = new DualPathPipeline(null, buffered);
    var event = enterEvent("Foo", "bar");

    pipeline.publish(event);
    awaitEvents(buffered, 1);

    assertThat(buffered.events()).containsExactly(event);
  }

  @Test
  void bothPathsReceiveEvent() throws InterruptedException {
    var syncReceived = new ArrayList<TraceEvent>();
    buffered = new BufferedEventConsumer(4);
    pipeline = new DualPathPipeline(syncReceived::add, buffered);
    var event = enterEvent("Foo", "bar");

    pipeline.publish(event);
    awaitEvents(buffered, 1);

    assertThat(syncReceived).containsExactly(event);
    assertThat(buffered.events()).containsExactly(event);
  }

  @Test
  void closeStopsBestEffortConsumer() throws InterruptedException {
    buffered = new BufferedEventConsumer(4);
    pipeline = new DualPathPipeline(null, buffered);

    pipeline.close();
    Thread.sleep(50);

    assertThat(buffered.consumerAlive()).isFalse();
  }

  @Test
  void syncOnlyPipelineHasNoOverhead() {
    var received = new ArrayList<TraceEvent>();
    pipeline = new DualPathPipeline(received::add);

    pipeline.publish(enterEvent("A", "a"));
    pipeline.close();

    assertThat(received).hasSize(1);
  }

  @Test
  void flushDelegatestoBufferedConsumer() {
    buffered = new BufferedEventConsumer(16, false);
    pipeline = new DualPathPipeline(null, buffered);
    pipeline.publish(enterEvent("A", "a"));

    pipeline.flush();

    assertThat(buffered.events()).hasSize(1);
  }

  @Test
  void eventsReturnsFlushedEvents() {
    buffered = new BufferedEventConsumer(16, false);
    pipeline = new DualPathPipeline(null, buffered);
    var event = enterEvent("A", "a");
    pipeline.publish(event);

    pipeline.flush();

    assertThat(pipeline.events()).containsExactly(event);
  }

  @Test
  void eventsReturnsEmptyWithoutConsumer() {
    pipeline = new DualPathPipeline(e -> {});

    assertThat(pipeline.events()).isEmpty();
  }

  @Test
  void clearRemovesAllEvents() {
    buffered = new BufferedEventConsumer(16, false);
    pipeline = new DualPathPipeline(null, buffered);
    pipeline.publish(enterEvent("A", "a"));
    pipeline.flush();
    assertThat(pipeline.events()).hasSize(1);

    pipeline.clear();

    assertThat(pipeline.events()).isEmpty();
  }

  @Test
  void closeToleratesFailingBestEffortConsumer() {
    Consumer<TraceEvent> failing = new FailingCloseConsumer();
    pipeline = new DualPathPipeline(null, failing);

    assertThatNoException().isThrownBy(() -> pipeline.close());
  }

  private static void awaitEvents(BufferedEventConsumer consumer, int count)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + 2000;
    while (consumer.events().size() < count && System.currentTimeMillis() < deadline) {
      Thread.sleep(5);
    }
  }

  @Test
  void listenerConstructorComposesDefaultRetention() {
    var received = new ArrayList<TraceEvent>();
    pipeline = new DualPathPipeline(received::add);
    var event = enterEvent("Foo", "bar");

    pipeline.publish(event);
    pipeline.flush();

    assertThat(received).containsExactly(event);
    assertThat(pipeline.retainsEvents()).isTrue();
    assertThat(pipeline.events()).containsExactly(event);
  }

  @Test
  void narrationOnlyRetainsNothingByChoice() {
    var received = new ArrayList<TraceEvent>();
    pipeline = DualPathPipeline.narrationOnly(received::add);
    var event = enterEvent("Foo", "bar");

    pipeline.publish(event);
    pipeline.flush();

    assertThat(received).containsExactly(event);
    assertThat(pipeline.retainsEvents()).isFalse();
    assertThat(pipeline.events()).isEmpty();
  }

  @Test
  void noArgConstructorBuildsTheDefaultCoreTopology() {
    pipeline = new DualPathPipeline();
    var event = enterEvent("Foo", "bar");

    pipeline.publish(event);
    pipeline.flush();

    assertThat(pipeline.retainsEvents()).isTrue();
    assertThat(pipeline.events()).containsExactly(event);
  }

  @Test
  void alternativeRetainingConsumerIsRecognizedAsRetaining() {
    var store = new FakeRetainingConsumer();
    pipeline = new DualPathPipeline(null, store);

    assertThat(pipeline.retainsEvents()).isTrue();
  }

  @Test
  void alternativeRetainingConsumerReceivesRetentionOperations() {
    var store = new FakeRetainingConsumer();
    pipeline = new DualPathPipeline(null, store);
    var event = enterEvent("Foo", "bar");
    var spanIds = Set.of(event.spanContext().spanId());

    pipeline.publish(event);
    pipeline.flush();
    assertThat(pipeline.events()).containsExactly(event);
    pipeline.clearSpans(spanIds);
    assertThat(store.removedSpans).isEqualTo(spanIds);
    pipeline.clear();
    assertThat(pipeline.events()).isEmpty();
    assertThat(store.flushed).isTrue();
  }

  /** Minimal alternative retention: proves the pipeline depends on the contract, not the class. */
  private static final class FakeRetainingConsumer implements RetainingConsumer {
    private final List<TraceEvent> received = new ArrayList<>();
    private final Set<SpanId> removedSpans = new HashSet<>();
    private boolean flushed;

    @Override
    public void accept(TraceEvent event) {
      received.add(event);
    }

    @Override
    public void flush() {
      flushed = true;
    }

    @Override
    public List<TraceEvent> events() {
      return List.copyOf(received);
    }

    @Override
    public void clear() {
      received.clear();
    }

    @Override
    public void removeSpans(Set<SpanId> spanIds) {
      removedSpans.addAll(spanIds);
    }
  }

  /**
   * The application-facing half of the closed-publisher bug: {@code captureTrace()} reaches {@code
   * flush()} through this class, and no {@code TraceBoundary} wraps that path.
   */
  @Test
  void aFlushAfterCloseIsNotTheCallersException() {
    buffered = new BufferedEventConsumer(4, false);
    pipeline = new DualPathPipeline(null, buffered);
    pipeline.close();
    var late = enterEvent("Late", "call");

    pipeline.publish(late);
    pipeline.flush();

    assertThat(pipeline.events()).containsExactly(late);
  }

  private static TraceEvent.EnterEvent enterEvent(String className, String methodName) {
    return new TraceEvent.EnterEvent(
        TestSpanContext.create(),
        System.nanoTime(),
        new MethodSignature(className, methodName, List.of()));
  }

  /**
   * The pipeline answers for whatever holds the best-effort slot: a capture asking "has everything
   * published arrived?" must reach the ring, and a topology with nothing buffered must say yes
   * rather than make the caller spin.
   */
  @Test
  void drainedReportsTheRetentionsAnswer() {
    buffered = new BufferedEventConsumer(16, false);
    pipeline = new DualPathPipeline(null, buffered);

    pipeline.publish(enterEvent("Svc", "run"));

    assertThat(pipeline.drained()).isFalse();

    pipeline.flush();

    assertThat(pipeline.drained()).isTrue();
  }

  /**
   * The {@code drained()} default exists for retentions that keep events as they arrive — an
   * alternative transport satisfying the same contract, or a test double. They have nothing in
   * flight, ever, so a capture must not spin waiting for them.
   */
  @Test
  void aRetentionThatKeepsEventsSynchronouslyIsAlwaysDrained() {
    var retention = new SynchronousRetention();
    pipeline = new DualPathPipeline(null, retention);

    pipeline.publish(enterEvent("Svc", "run"));

    assertThat(pipeline.drained()).isTrue();
    assertThat(pipeline.events()).hasSize(1);
  }

  /** A retention with no buffer at all: it takes the default {@code drained()} answer. */
  private static final class SynchronousRetention implements RetainingConsumer {
    private final java.util.List<TraceEvent> kept = new ArrayList<>();

    @Override
    public void accept(TraceEvent event) {
      kept.add(event);
    }

    @Override
    public void flush() {
      // nothing buffers, so there is nothing to drain
    }

    @Override
    public java.util.List<TraceEvent> events() {
      return java.util.List.copyOf(kept);
    }

    @Override
    public void clear() {
      kept.clear();
    }

    @Override
    public void removeSpans(java.util.Set<ai.narrativetrace.api.event.SpanId> spanIds) {
      kept.removeIf(
          event -> {
            var spanId = TraceEvent.spanIdOf(event);
            return spanId != null && spanIds.contains(spanId);
          });
    }
  }

  @Test
  void aPipelineWhoseBestEffortSlotRetainsNothingIsAlwaysDrained() {
    pipeline = new DualPathPipeline(null, event -> {});

    pipeline.publish(enterEvent("Svc", "run"));

    assertThat(pipeline.drained())
        .as("nothing buffers, so a caller waiting for a drain would wait forever for nothing")
        .isTrue();
  }

  private static final class FailingCloseConsumer implements Consumer<TraceEvent>, AutoCloseable {
    @Override
    public void accept(TraceEvent event) {}

    @Override
    public void close() {
      throw new RuntimeException("close failed");
    }
  }
}
