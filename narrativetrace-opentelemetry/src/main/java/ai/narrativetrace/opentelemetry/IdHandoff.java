/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.opentelemetry;

import ai.narrativetrace.api.event.SpanContext;

/**
 * The NarrativeTrace ids of the one span the calling thread is about to start, offered to {@link
 * NarrativeTraceIdGenerator} for the duration of a single {@code startSpan} call.
 *
 * <p>INTENT: Kept apart from the generator so the live listener and the batch exporter can offer
 * ids without loading any OpenTelemetry SDK class: an application with only the OpenTelemetry API
 * on its classpath offers ids nobody takes, and nothing breaks.
 *
 * <p><b>@pattern</b> Thread-local hand-off, set right before {@code startSpan} and cleared right
 * after it (try/finally at both call sites). The SDK generates a span's ids synchronously on the
 * starting thread, so the generator sees exactly this span's offer; each id is taken at most once,
 * so a span some span processor starts from inside {@code startSpan} gets ordinary ids.
 */
final class IdHandoff {

  /** Which NarrativeTrace path started the span — the two must never export one context twice. */
  enum Source {
    LIVE,
    BATCH
  }

  private static final ThreadLocal<IdHandoff> CURRENT = new ThreadLocal<>();

  final String traceId;
  final String spanId;
  final Source source;
  private final IdHandoff previous;
  boolean spanIdTaken;
  boolean traceIdTaken;

  /** Whether this offer may be mirrored at all; decided once, at the first id the SDK asks for. */
  Boolean granted;

  private IdHandoff(SpanContext spanContext, Source source, IdHandoff previous) {
    this.traceId = spanContext.traceId().toString();
    this.spanId = spanContext.spanId().toString();
    this.source = source;
    this.previous = previous;
  }

  /**
   * Offers the ids of the span about to start on this thread.
   *
   * @param spanContext the NarrativeTrace context of that span; {@code null} offers nothing
   * @param source the path starting it
   * @return the offer to pass to {@link #withdraw}, or {@code null} when nothing was offered
   */
  static IdHandoff offer(SpanContext spanContext, Source source) {
    if (spanContext == null) {
      return null;
    }
    var offer = new IdHandoff(spanContext, source, CURRENT.get());
    CURRENT.set(offer);
    return offer;
  }

  /** Withdraws an offer once its span has started, restoring any offer it shadowed. */
  static void withdraw(IdHandoff offer) {
    if (offer == null) {
      return;
    }
    if (offer.previous == null) {
      CURRENT.remove();
    } else {
      CURRENT.set(offer.previous);
    }
  }

  /** The calling thread's open offer, or {@code null}. */
  static IdHandoff current() {
    return CURRENT.get();
  }
}
