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
import ai.narrativetrace.api.event.TestSpanContext;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.spi.TraceEventListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * No observer failure crosses the publish boundary, whatever kind of throwable it is.
 *
 * <p>INTENT: The 2026-09-01 bug hunt found that {@link DualPathPipeline#publish} isolated a
 * listener's {@code RuntimeException} and let its {@code AssertionError} through, and that {@link
 * ListenerFanoutConsumer} had the same {@code Exception}-only boundary. The class comment on the
 * pipeline has always promised isolation without qualification; these tests are what makes the
 * promise true for {@link Error} subclasses — an assertion in a listener's own build, a {@code
 * LinkageError} from a shaded dependency, an initializer failure.
 */
class PipelineTotalityTest {

  private DualPathPipeline pipeline;

  @AfterEach
  void tearDown() {
    if (pipeline != null) {
      pipeline.close();
    }
  }

  /** A listener that throws the given throwable on its first event and succeeds afterwards. */
  private static final class FailOnceListener implements TraceEventListener {
    private final RuntimeExceptionFactory factory;
    private final List<TraceEvent> received = new ArrayList<>();
    private int calls;

    FailOnceListener(RuntimeExceptionFactory factory) {
      this.factory = factory;
    }

    @Override
    public void onEvent(TraceEvent event) {
      calls++;
      if (calls == 1) {
        throw factory.create();
      }
      received.add(event);
    }
  }

  /** Produces the throwable a fixture should raise; an interface so {@link Error} is allowed. */
  @FunctionalInterface
  private interface RuntimeExceptionFactory {
    Error create();
  }

  @Test
  void aSynchronousListenerThrowingAnAssertionErrorNeverReachesTheCaller() {
    var buffered = new BufferedEventConsumer(8, false);
    pipeline =
        new DualPathPipeline(
            event -> {
              throw new AssertionError("listener assertion");
            },
            buffered);
    var event = enterEvent();

    assertThatNoException().isThrownBy(() -> pipeline.publish(event));

    pipeline.flush();
    assertThat(pipeline.events()).containsExactly(event);
  }

  @Test
  void aBestEffortConsumerThrowingALinkageErrorNeverReachesTheCaller() {
    var received = new ArrayList<TraceEvent>();
    pipeline =
        new DualPathPipeline(
            received::add,
            event -> {
              throw new NoClassDefFoundError("com/example/Missing");
            });
    var event = enterEvent();

    assertThatNoException().isThrownBy(() -> pipeline.publish(event));

    assertThat(received).containsExactly(event);
  }

  @Test
  void bothPathsThrowingErrorsStillLeavesTheCallerUntouched() {
    pipeline =
        new DualPathPipeline(
            event -> {
              throw new AssertionError("sync");
            },
            event -> {
              throw new StackOverflowError();
            });

    assertThatNoException().isThrownBy(() -> pipeline.publish(enterEvent()));
  }

  @Test
  void aDiscoveredListenerThrowingAnErrorStillLeavesRetentionIntact() {
    var retention = new BufferedEventConsumer(8, false);
    var fanout =
        new ListenerFanoutConsumer(
            retention,
            List.of(
                event -> {
                  throw new AssertionError("listener assertion");
                }));
    var event = enterEvent();

    assertThatNoException().isThrownBy(() -> fanout.accept(event));

    fanout.flush();
    assertThat(fanout.events()).containsExactly(event);
    fanout.close();
  }

  @Test
  void aListenerThatFailsOnceIsNeverCalledAgain() {
    var failing = new FailOnceListener(() -> new AssertionError("first event"));
    var retention = new BufferedEventConsumer(8, false);
    var fanout = new ListenerFanoutConsumer(retention, List.of(failing));

    fanout.accept(enterEvent());
    fanout.accept(enterEvent());
    fanout.accept(enterEvent());

    assertThat(failing.calls).as("disabled after its first failure").isOne();
    assertThat(failing.received).isEmpty();
    fanout.close();
  }

  @Test
  void oneDisabledListenerDoesNotSilenceItsNeighbours() {
    var healthy = new ArrayList<TraceEvent>();
    var retention = new BufferedEventConsumer(8, false);
    var fanout =
        new ListenerFanoutConsumer(
            retention,
            List.of(
                event -> {
                  throw new LinkageError("shaded dependency");
                },
                healthy::add));
    var first = enterEvent();
    var second = enterEvent();

    fanout.accept(first);
    fanout.accept(second);

    assertThat(healthy).containsExactly(first, second);
    fanout.close();
  }

  @Test
  void aConsumerWhoseCloseThrowsAnErrorDoesNotFailShutdown() {
    var closing = new DualPathPipeline(null, new ErrorOnCloseConsumer());

    assertThatNoException().isThrownBy(closing::close);
  }

  @Test
  void aRetentionWhoseCloseThrowsAnErrorDoesNotFailFanoutShutdown() {
    var fanout = new ListenerFanoutConsumer(new ErrorOnCloseRetention(), List.of(event -> {}));

    assertThatNoException().isThrownBy(fanout::close);
  }

  @Test
  void aWatchdogCallbackThrowingAnErrorDoesNotKillTheWatchdogThread() {
    var fired = new java.util.concurrent.atomic.AtomicInteger();

    assertThatNoException()
        .isThrownBy(
            () -> {
              try (var watchdog =
                  new ConsumerWatchdog(
                      () -> 1L,
                      0,
                      5,
                      () -> {
                        fired.incrementAndGet();
                        throw new AssertionError("watchdog callback");
                      })) {
                await(fired);
              }
            });
    assertThat(fired.get()).isPositive();
  }

  private static void await(java.util.concurrent.atomic.AtomicInteger fired) {
    var deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    while (fired.get() == 0 && System.nanoTime() < deadline) {
      java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
    }
  }

  private static final class ErrorOnCloseConsumer implements Consumer<TraceEvent>, AutoCloseable {
    @Override
    public void accept(TraceEvent event) {}

    @Override
    public void close() {
      throw new AssertionError("close assertion");
    }
  }

  private static final class ErrorOnCloseRetention implements RetainingConsumer, AutoCloseable {
    @Override
    public void accept(TraceEvent event) {}

    @Override
    public void flush() {}

    @Override
    public List<TraceEvent> events() {
      return List.of();
    }

    @Override
    public void clear() {}

    @Override
    public void removeSpans(java.util.Set<ai.narrativetrace.api.event.SpanId> spanIds) {}

    @Override
    public void close() {
      throw new AssertionError("close assertion");
    }
  }

  private static TraceEvent.EnterEvent enterEvent() {
    return new TraceEvent.EnterEvent(
        TestSpanContext.create(), System.nanoTime(), new MethodSignature("Foo", "bar", List.of()));
  }
}
