/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceOutcome;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranslationSubscriberTest {

  private final List<String> sink = new ArrayList<>();

  private static GlossaryTerm term(String term, TermKind kind, Map<String, String> translations) {
    return new GlossaryTerm(
        term,
        "billing",
        kind,
        TermStatus.CURATED,
        null,
        translations,
        List.of(),
        List.of(),
        LocalDate.of(2026, 8, 11));
  }

  private static Glossary billingGlossary(GlossaryTerm... terms) {
    return new Glossary(
        1,
        Map.of("billing", new BoundedContext("billing", List.of("com.acme.billing"), null)),
        List.of(terms));
  }

  private static Glossary chargeGlossary() {
    return billingGlossary(term("charge", TermKind.VERB_PHRASE, Map.of("es", "cobrar")));
  }

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

  private static SpanContext childSpanContext(SpanContext parent) {
    return SpanContext.builder(parent.traceId(), SpanIdGenerator.spanId())
        .parentSpanId(parent.spanId())
        .build();
  }

  private static TraceEvent.ExitEvent exitEvent(SpanContext spanContext, String renderedValue) {
    return new TraceEvent.ExitEvent(
        spanContext, System.nanoTime(), new TraceOutcome.Returned(renderedValue), null);
  }

  @Test
  void rendersATranslatedEnterLineToTheSink() {
    var subscriber = new TranslationSubscriber(chargeGlossary(), "es", sink::add);

    subscriber.onNext(
        enterEvent(
            rootSpanContext(),
            "PaymentService",
            "charge",
            new ParameterCapture("amount", "74.97", false)));

    assertThat(sink).containsExactly("PaymentService.cobrar (charge) (amount: 74.97)");
  }

  @Test
  void depthAccumulatesWithinOneTraceAndTracesStayIsolated() {
    var subscriber = new TranslationSubscriber(chargeGlossary(), "es", sink::add);
    var rootA = rootSpanContext();
    var rootB = rootSpanContext();

    subscriber.onNext(enterEvent(rootA, "OrderService", "placeOrder"));
    subscriber.onNext(enterEvent(childSpanContext(rootA), "PaymentService", "charge"));
    subscriber.onNext(enterEvent(rootB, "InvoiceService", "print"));

    assertThat(sink)
        .containsExactly(
            "OrderService.placeOrder()",
            "  PaymentService.cobrar (charge) ()",
            "InvoiceService.print()");
  }

  @Test
  void rootExitEmitsTheGapsFooterAndDropsTheTraceState() {
    var subscriber = new TranslationSubscriber(billingGlossary(), "es", sink::add);
    var root = rootSpanContext();
    var child = childSpanContext(root);

    subscriber.onNext(enterEvent(root, "OrderService", "audit"));
    subscriber.onNext(exitEvent(root, "\"done\""));
    sink.clear();
    subscriber.onNext(enterEvent(child, "PaymentService", "charge"));

    assertThat(sink).containsExactly("PaymentService.charge()");
  }

  @Test
  void rootExitFooterListsTheAccumulatedGaps() {
    var subscriber = new TranslationSubscriber(billingGlossary(), "es", sink::add);
    var root = rootSpanContext();

    subscriber.onNext(enterEvent(root, "OrderService", "audit"));
    subscriber.onNext(exitEvent(root, "\"done\""));

    assertThat(sink)
        .containsExactly(
            "OrderService.audit()",
            "  -> devuelve \"done\"",
            "---",
            "Vacíos del glosario:",
            "- audit");
  }

  @Test
  void capacityEvictionEmitsTheEvictedTraceFooter() {
    var subscriber =
        new TranslationSubscriber(
            billingGlossary(), "es", sink::add, 1, java.time.Duration.ofMinutes(5));

    subscriber.onNext(enterEvent(rootSpanContext(), "OrderService", "audit"));
    subscriber.onNext(enterEvent(rootSpanContext(), "InvoiceService", "print"));

    assertThat(sink)
        .containsExactly(
            "OrderService.audit()",
            "---",
            "Vacíos del glosario:",
            "- audit",
            "InvoiceService.print()");
  }

  @Test
  void concurrencyEventsWithoutTraceIdentityAreSkipped() {
    var subscriber = new TranslationSubscriber(chargeGlossary(), "es", sink::add);

    subscriber.onNext(new TraceEvent.ForkCreatedEvent("group-1", System.nanoTime()));
    subscriber.onNext(new TraceEvent.MergeEvent("group-1", 2, System.nanoTime()));
    subscriber.onNext(new TraceEvent.FireAndForgetEvent("group-1", System.nanoTime()));

    assertThat(sink).isEmpty();
  }

  @Test
  void onSubscribeRequestsAnUnboundedStream() {
    var subscriber = new TranslationSubscriber(chargeGlossary(), "es", sink::add);
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
    var subscriber = new TranslationSubscriber(chargeGlossary(), "es", sink::add);

    subscriber.onError(new IllegalStateException("upstream died"));
    subscriber.onComplete();

    assertThat(sink).isEmpty();
  }

  @Test
  void rejectsInvalidConstructionArguments() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new TranslationSubscriber(null, "es", sink::add))
        .isInstanceOf(IllegalArgumentException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new TranslationSubscriber(chargeGlossary(), " ", sink::add))
        .isInstanceOf(IllegalArgumentException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                new TranslationSubscriber(
                    chargeGlossary(), "es", (java.util.function.Consumer<String>) null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void defaultSinkEmitsToThePerLocaleI18nLogger() {
    var logbackLogger =
        (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("narrativetrace.i18n.es");
    var appender =
        new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    appender.start();
    logbackLogger.setLevel(ch.qos.logback.classic.Level.ALL);
    logbackLogger.detachAndStopAllAppenders();
    logbackLogger.addAppender(appender);
    try {
      var subscriber = new TranslationSubscriber(chargeGlossary(), "es");

      subscriber.onNext(enterEvent(rootSpanContext(), "PaymentService", "charge"));

      assertThat(appender.list).hasSize(1);
      assertThat(appender.list.get(0).getFormattedMessage())
          .isEqualTo("PaymentService.cobrar (charge) ()");
    } finally {
      logbackLogger.detachAndStopAllAppenders();
    }
  }

  @Test
  void fileVariantWritesEachTraceToItsOwnMarkdownFile(@TempDir Path dir) throws Exception {
    var subscriber = new TranslationSubscriber(billingGlossary(), "es", dir);
    var rootA = rootSpanContext();
    var rootB = rootSpanContext();

    subscriber.onNext(enterEvent(rootA, "OrderService", "audit"));
    subscriber.onNext(enterEvent(rootB, "InvoiceService", "print"));
    subscriber.onNext(exitEvent(rootA, "\"done\""));

    assertThat(Files.readString(dir.resolve(rootA.traceId() + ".md")))
        .isEqualTo(
            """
            OrderService.audit()
              -> devuelve "done"
            ---
            Vacíos del glosario:
            - audit
            """);
    assertThat(Files.readString(dir.resolve(rootB.traceId() + ".md")))
        .isEqualTo("InvoiceService.print()\n");
  }

  @Test
  void fileVariantRoutesEvictionFootersToTheEvictedTraceFile(@TempDir Path dir) throws Exception {
    var subscriber =
        new TranslationSubscriber(billingGlossary(), "es", dir, 1, java.time.Duration.ofMinutes(5));
    var rootA = rootSpanContext();
    var rootB = rootSpanContext();

    subscriber.onNext(enterEvent(rootA, "OrderService", "audit"));
    subscriber.onNext(enterEvent(rootB, "InvoiceService", "print"));

    assertThat(Files.readString(dir.resolve(rootA.traceId() + ".md")))
        .isEqualTo(
            """
            OrderService.audit()
            ---
            Vacíos del glosario:
            - audit
            """);
    assertThat(Files.readString(dir.resolve(rootB.traceId() + ".md")))
        .isEqualTo("InvoiceService.print()\n");
  }

  @Test
  void fileVariantDropsLinesItCannotWriteAndKeepsServingOtherTraces(@TempDir Path dir)
      throws Exception {
    var subscriber = new TranslationSubscriber(chargeGlossary(), "es", dir);
    var blocked = rootSpanContext();
    var healthy = rootSpanContext();
    Files.createDirectory(dir.resolve(blocked.traceId() + ".md"));

    subscriber.onNext(enterEvent(blocked, "PaymentService", "charge"));
    subscriber.onNext(enterEvent(blocked, "PaymentService", "charge"));
    subscriber.onNext(enterEvent(healthy, "PaymentService", "charge"));

    assertThat(Files.readString(dir.resolve(healthy.traceId() + ".md")))
        .isEqualTo("PaymentService.cobrar (charge) ()\n");
  }

  @Test
  void fileVariantReportsAWriteFailureOnlyOnce(@TempDir Path dir) throws Exception {
    var logbackLogger =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(TranslationFileSink.class);
    var appender =
        new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    appender.start();
    logbackLogger.addAppender(appender);
    try {
      var subscriber = new TranslationSubscriber(chargeGlossary(), "es", dir);
      var blocked = rootSpanContext();
      Files.createDirectory(dir.resolve(blocked.traceId() + ".md"));

      subscriber.onNext(enterEvent(blocked, "PaymentService", "charge"));
      subscriber.onNext(enterEvent(blocked, "PaymentService", "charge"));

      assertThat(appender.list).hasSize(1);
      assertThat(appender.list.get(0).getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
    } finally {
      logbackLogger.detachAppender(appender);
    }
  }

  @Test
  void fileVariantFailsFastOnAnUncreatableOutputDirectory(@TempDir Path dir) throws Exception {
    var occupied = dir.resolve("occupied");
    Files.writeString(occupied, "not a directory");

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new TranslationSubscriber(chargeGlossary(), "es", occupied))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("occupied");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new TranslationSubscriber(chargeGlossary(), "es", (Path) null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void receivesEventsLiveFromABufferedEventConsumer() throws Exception {
    var latch = new java.util.concurrent.CountDownLatch(1);
    var lines = java.util.Collections.synchronizedList(new ArrayList<String>());
    var subscriber =
        new TranslationSubscriber(
            chargeGlossary(),
            "es",
            line -> {
              lines.add(line);
              latch.countDown();
            });
    try (var consumer = new ai.narrativetrace.core.pipeline.BufferedEventConsumer(16)) {
      consumer.subscribe(subscriber);

      consumer.accept(enterEvent(rootSpanContext(), "PaymentService", "charge"));

      assertThat(latch.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }
    assertThat(lines).containsExactly("PaymentService.cobrar (charge) ()");
  }
}
