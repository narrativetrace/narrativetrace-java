/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TestSpanContext;
import ai.narrativetrace.api.event.TraceEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class DropCountingHandlerTest {

  @Test
  void initialDropCountIsZero() {
    var handler = new DropCountingHandler();

    assertThat(handler.droppedCount()).isZero();
  }

  @Test
  void acceptIncrementsDropCount() {
    var handler = new DropCountingHandler();

    handler.test(null, enterEvent("Svc", "run"));
    handler.test(null, enterEvent("Svc", "stop"));

    assertThat(handler.droppedCount()).isEqualTo(2);
  }

  @Test
  void testReturnsFalseToConfirmDrop() {
    var handler = new DropCountingHandler();

    assertThat(handler.test(null, enterEvent("Svc", "run"))).isFalse();
  }

  @Test
  void countsDropsWhenSubmissionPublisherOverflows() throws Exception {
    var handler = new DropCountingHandler();
    var publisher = new java.util.concurrent.SubmissionPublisher<TraceEvent>(Runnable::run, 1);
    var latch = new java.util.concurrent.CountDownLatch(1);
    publisher.subscribe(
        new java.util.concurrent.Flow.Subscriber<TraceEvent>() {
          @Override
          public void onSubscribe(java.util.concurrent.Flow.Subscription s) {
            // Don't request — subscriber never pulls, so buffer fills immediately
          }

          @Override
          public void onNext(TraceEvent item) {}

          @Override
          public void onError(Throwable throwable) {}

          @Override
          public void onComplete() {
            latch.countDown();
          }
        });

    publisher.offer(enterEvent("Svc", "a"), 1, java.util.concurrent.TimeUnit.MILLISECONDS, handler);
    publisher.offer(enterEvent("Svc", "b"), 1, java.util.concurrent.TimeUnit.MILLISECONDS, handler);

    publisher.close();
    latch.await(2, java.util.concurrent.TimeUnit.SECONDS);
    assertThat(handler.droppedCount()).isGreaterThanOrEqualTo(1);
  }

  private static TraceEvent.EnterEvent enterEvent(String className, String methodName) {
    return new TraceEvent.EnterEvent(
        TestSpanContext.create(),
        System.nanoTime(),
        new MethodSignature(className, methodName, List.of()));
  }
}
