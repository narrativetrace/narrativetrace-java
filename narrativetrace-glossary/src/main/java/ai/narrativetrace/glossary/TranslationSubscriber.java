/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.core.export.CanonicalEntryMapper;
import ai.narrativetrace.core.pipeline.PerishableMap;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import org.slf4j.LoggerFactory;

/**
 * Live translated-trace stream: a {@link Flow.Subscriber} that renders every pipeline event into
 * one locale as it arrives.
 *
 * <p>INTENT: The pipeline-subscriber shape of the glossary design (Phase 7). Subscribe it to {@code
 * BufferedEventConsumer}'s publisher seam; each event maps to a canonical entry ({@link
 * CanonicalEntryMapper#fromEvent}) and renders through {@link TraceTranslationView#renderEntry}
 * with per-trace state held in a {@link PerishableMap}. Rendered lines go to the sink one line at a
 * time, without trailing newlines. Two sink shapes ship: a {@link Consumer}-of-lines (default: the
 * {@code narrativetrace.i18n.<locale>} SLF4J logger) and a file-writing variant ({@link Path}
 * constructors) appending each trace's lines live to {@code <outputDir>/<traceId>.md} — the
 * pipeline-shaped replacement for the retired build-time {@code translateTraces} task.
 *
 * <p>Lifecycle of a trace's state: created on the trace's first event, dropped when its root span
 * exits (the glossary-gaps footer for that trace is emitted at that point), and evicted by capacity
 * or TTL for traces that never complete (the footer is emitted on eviction too). A late event
 * arriving after its trace's state was dropped renders at depth 0 — degraded, never wrong.
 *
 * <p><b>@llmNote</b> The async pipeline path is lossy under load by design (drain-loop shedding,
 * bounded offer timeout) — this subscriber renders what arrives and never blocks the capture path.
 * Concurrency events (fork/join/fire-and-forget) carry no trace identity and are not rendered in
 * translated views yet. Context resolution relies on the captured {@code nt.package} field (schema
 * 1.2); there is no class-package index on the live path.
 */
public final class TranslationSubscriber implements Flow.Subscriber<TraceEvent> {

  /** Default cap on concurrently tracked traces. */
  public static final int DEFAULT_CAPACITY = 1024;

  /** Default TTL after which an incomplete trace's state is evicted. */
  public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

  private final TraceTranslationView view;
  private final String locale;
  private final TraceLineSink sink;
  private final PerishableMap<String, TraceState> states;

  /** Per-trace render state paired with its trace id, so eviction still knows the trace. */
  private record TraceState(String traceId, TraceTranslationView.RenderState render) {}

  /** Receives each rendered line together with the trace it belongs to. */
  @FunctionalInterface
  interface TraceLineSink {
    void emit(String traceId, String line);
  }

  /**
   * Creates a subscriber emitting to the {@code narrativetrace.i18n.<locale>} SLF4J logger at
   * {@code INFO}, with default capacity and TTL.
   *
   * @param glossary glossary providing per-context terms and translations
   * @param locale target locale tag (e.g. {@code "es"}); must not be blank
   */
  public TranslationSubscriber(Glossary glossary, String locale) {
    this(glossary, locale, loggerSink(locale));
  }

  /**
   * Creates a subscriber with default capacity and TTL.
   *
   * @param glossary glossary providing per-context terms and translations
   * @param locale target locale tag (e.g. {@code "es"}); must not be blank
   * @param sink receives each rendered line, without a trailing newline
   */
  public TranslationSubscriber(Glossary glossary, String locale, Consumer<String> sink) {
    this(glossary, locale, sink, DEFAULT_CAPACITY, DEFAULT_TTL);
  }

  /**
   * Creates a file-writing subscriber: each trace's translated lines append live to {@code
   * <outputDir>/<traceId>.md}, with default capacity and TTL.
   *
   * <p>This is the replacement for the retired build-time {@code translateTraces} task — the same
   * rendering, attached to any run's pipeline instead of stored test artifacts. Writing is
   * best-effort: an unwritable file is reported once per subscriber and never propagates.
   *
   * @param glossary glossary providing per-context terms and translations
   * @param locale target locale tag (e.g. {@code "es"}); must not be blank
   * @param outputDir directory receiving one {@code <traceId>.md} per trace; created if absent
   */
  public TranslationSubscriber(Glossary glossary, String locale, Path outputDir) {
    this(glossary, locale, outputDir, DEFAULT_CAPACITY, DEFAULT_TTL);
  }

  /**
   * Creates a file-writing subscriber with explicit state bounds.
   *
   * @param glossary glossary providing per-context terms and translations
   * @param locale target locale tag (e.g. {@code "es"}); must not be blank
   * @param outputDir directory receiving one {@code <traceId>.md} per trace; created if absent
   * @param capacity maximum concurrently tracked traces before the oldest state is evicted
   * @param ttl maximum age of an incomplete trace's state before eviction
   */
  public TranslationSubscriber(
      Glossary glossary, String locale, Path outputDir, int capacity, Duration ttl) {
    this(glossary, locale, new TranslationFileSink(outputDir), capacity, ttl);
  }

  private static Consumer<String> loggerSink(String locale) {
    return LoggerFactory.getLogger("narrativetrace.i18n." + locale)::info;
  }

  /**
   * Creates a subscriber with explicit state bounds.
   *
   * @param glossary glossary providing per-context terms and translations
   * @param locale target locale tag (e.g. {@code "es"}); must not be blank
   * @param sink receives each rendered line, without a trailing newline
   * @param capacity maximum concurrently tracked traces before the oldest state is evicted
   * @param ttl maximum age of an incomplete trace's state before eviction
   */
  public TranslationSubscriber(
      Glossary glossary, String locale, Consumer<String> sink, int capacity, Duration ttl) {
    this(glossary, locale, adapt(sink), capacity, ttl);
  }

  private static TraceLineSink adapt(Consumer<String> sink) {
    if (sink == null) {
      throw new IllegalArgumentException("sink must not be null");
    }
    return (traceId, line) -> sink.accept(line);
  }

  private TranslationSubscriber(
      Glossary glossary, String locale, TraceLineSink sink, int capacity, Duration ttl) {
    if (locale == null || locale.isBlank()) {
      throw new IllegalArgumentException("locale must not be blank");
    }
    this.view = new TraceTranslationView(glossary, className -> null);
    this.locale = locale;
    this.sink = sink;
    this.states = new PerishableMap<>(capacity, ttl, this::emitFooter);
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    subscription.request(Long.MAX_VALUE);
  }

  @Override
  public void onNext(TraceEvent event) {
    var entry = CanonicalEntryMapper.fromEvent(event);
    if (entry.traceId() == null) {
      return; // concurrency events: no trace identity, not rendered in translated views yet
    }
    var state = states.get(entry.traceId());
    if (state == null) {
      state = new TraceState(entry.traceId(), view.newRenderState(locale));
      states.put(entry.traceId(), state);
    }
    emitLines(state.traceId(), view.renderEntry(entry, state.render()));
    if (isRootExit(entry)) {
      var finished = states.remove(entry.traceId());
      if (finished != null) {
        emitFooter(finished);
      }
    }
  }

  @Override
  public void onError(Throwable throwable) {
    // Best-effort derived view: a failed upstream ends the stream; nothing to clean up.
  }

  @Override
  public void onComplete() {
    // Traces still open at stream end never saw their root exit; their footers are not emitted.
  }

  private static boolean isRootExit(ai.narrativetrace.core.export.CanonicalEntry entry) {
    return "method_exit".equals(entry.ntEventType()) && entry.parentSpanId() == null;
  }

  private void emitFooter(TraceState state) {
    emitLines(state.traceId(), view.renderGapsFooter(state.render()));
  }

  private void emitLines(String traceId, String text) {
    text.lines().filter(line -> !line.isEmpty()).forEach(line -> sink.emit(traceId, line));
  }
}
