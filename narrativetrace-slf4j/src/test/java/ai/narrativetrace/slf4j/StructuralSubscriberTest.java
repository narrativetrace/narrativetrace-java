/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.slf4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceOutcome;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructuralSubscriberTest {

  private final List<String> sink = new ArrayList<>();

  private static SpanContext rootSpanContext() {
    return SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();
  }

  private static TraceEvent.EnterEvent enterEvent(
      SpanContext spanContext, String className, String methodName, ParameterCapture... params) {
    return new TraceEvent.EnterEvent(
        spanContext,
        System.nanoTime(),
        new MethodSignature(className, methodName, List.of(params)));
  }

  @Test
  void emitsOneValueFreeStructuralJsonLinePerEvent() {
    var subscriber = new StructuralSubscriber(sink::add);

    subscriber.onNext(
        enterEvent(
            rootSpanContext(),
            "PaymentService",
            "charge",
            new ParameterCapture("amount", "74.97", false)));

    assertThat(sink).hasSize(1);
    assertThat(sink.get(0))
        .contains("\"message\": \"→ PaymentService.charge(amount)\"")
        .contains("\"nt.eventType\": \"method_enter\"")
        .contains("[ELIDED]")
        .doesNotContain("74.97");
  }

  @Test
  void exitEventsCarryNoReturnValueOrExceptionMessage() {
    var subscriber = new StructuralSubscriber(sink::add);
    var span = rootSpanContext();

    subscriber.onNext(
        new TraceEvent.ExitEvent(
            span, System.nanoTime(), new TraceOutcome.Returned("\"secret-result\""), null));
    subscriber.onNext(
        new TraceEvent.ExitEvent(
            span,
            System.nanoTime(),
            new TraceOutcome.Threw(new IllegalStateException("secret failure detail")),
            null));

    assertThat(sink).hasSize(2);
    assertThat(sink.get(0)).doesNotContain("secret-result");
    assertThat(sink.get(1))
        .contains("IllegalStateException")
        .doesNotContain("secret failure detail");
  }

  @Test
  void defaultSinkEmitsToTheAiStructuralLogger() {
    var logbackLogger =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(StructuralSubscriber.LOGGER_NAME);
    var appender =
        new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    appender.start();
    logbackLogger.setLevel(ch.qos.logback.classic.Level.ALL);
    logbackLogger.detachAndStopAllAppenders();
    logbackLogger.addAppender(appender);
    try {
      var subscriber = new StructuralSubscriber();

      subscriber.onNext(enterEvent(rootSpanContext(), "PaymentService", "charge"));

      assertThat(appender.list).hasSize(1);
      assertThat(appender.list.get(0).getFormattedMessage()).contains("→ PaymentService.charge()");
    } finally {
      logbackLogger.detachAndStopAllAppenders();
    }
  }

  @Test
  void onSubscribeRequestsAnUnboundedStream() {
    var subscriber = new StructuralSubscriber(sink::add);
    var requested = new long[1];

    subscriber.onSubscribe(
        new java.util.concurrent.Flow.Subscription() {
          @Override
          public void request(long n) {
            requested[0] = n;
          }

          @Override
          public void cancel() {}
        });

    assertThat(requested[0]).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void streamEndSignalsAreTolerated() {
    var subscriber = new StructuralSubscriber(sink::add);

    subscriber.onError(new IllegalStateException("upstream died"));
    subscriber.onComplete();

    assertThat(sink).isEmpty();
  }

  @Test
  void rejectsANullSink() {
    assertThatThrownBy(() -> new StructuralSubscriber(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void receivesEventsLiveFromABufferedEventConsumer() throws Exception {
    var latch = new java.util.concurrent.CountDownLatch(1);
    var lines = java.util.Collections.synchronizedList(new ArrayList<String>());
    var subscriber =
        new StructuralSubscriber(
            line -> {
              lines.add(line);
              latch.countDown();
            });
    try (var consumer = new ai.narrativetrace.core.pipeline.BufferedEventConsumer(16)) {
      consumer.subscribe(subscriber);

      consumer.accept(enterEvent(rootSpanContext(), "PaymentService", "charge"));

      assertThat(latch.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }
    assertThat(lines).hasSize(1);
    assertThat(lines.get(0)).contains("→ PaymentService.charge()");
  }
}
