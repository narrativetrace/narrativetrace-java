/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.spi.TraceEventListener;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Best-effort slot holding retention and discovered listeners side by side.
 *
 * <p>INTENT: {@link DualPathPipeline}'s best-effort slot takes one consumer, but discovered {@link
 * TraceEventListener}s must observe events without displacing the retention that backs {@code
 * captureTrace()}. This composite makes them siblings: retention is fed first, then each listener.
 * Store operations pass straight through, so the pipeline cannot tell it is talking to a composite.
 *
 * <p><b>@llmNote</b> Only used when at least one listener is discovered. With none, the composition
 * root wires the retaining consumer directly and this class never enters the picture — the default
 * topology keeps exactly the shape and cost it had before extension points existed.
 *
 * <p><b>@sideEffects</b> A listener that throws <em>anything</em>, {@link Error} included, is
 * reported once and permanently disabled for this JVM; neither its failure nor its absence affects
 * retention, the other listeners, or the caller. The catch lives in {@link TraceBoundary}.
 */
final class ListenerFanoutConsumer implements RetainingConsumer, AutoCloseable {

  private final RetainingConsumer retention;
  private final List<TraceEventListener> listeners;
  private final Set<TraceEventListener> disabled = ConcurrentHashMap.newKeySet();

  ListenerFanoutConsumer(RetainingConsumer retention, List<TraceEventListener> listeners) {
    if (retention == null) {
      throw new IllegalArgumentException("retention must not be null");
    }
    if (listeners == null || listeners.isEmpty()) {
      throw new IllegalArgumentException("listeners must not be null or empty");
    }
    this.retention = retention;
    this.listeners = List.copyOf(listeners);
  }

  /**
   * The retention this composite wraps.
   *
   * <p>INTENT: Lets composition tests assert what the sibling behind the fan-out actually is — the
   * capacity a topology sized its ring to must not depend on whether a listener was discovered.
   */
  RetainingConsumer retention() {
    return retention;
  }

  @Override
  public void accept(TraceEvent event) {
    retention.accept(event);
    for (var listener : listeners) {
      notifyListener(listener, event);
    }
  }

  private void notifyListener(TraceEventListener listener, TraceEvent event) {
    if (disabled.contains(listener)) {
      return;
    }
    var failure = TraceBoundary.notify(listener, event);
    if (failure != null) {
      disabled.add(listener);
      System.err.println( // NOPMD
          "narrative-trace: listener "
              + listener.getClass().getName()
              + " threw and is disabled for this JVM ("
              + failure
              + ")");
    }
  }

  @Override
  public void flush() {
    retention.flush();
  }

  @Override
  public List<TraceEvent> events() {
    return retention.events();
  }

  @Override
  public void clear() {
    retention.clear();
  }

  @Override
  public void removeSpans(Set<SpanId> spanIds) {
    retention.removeSpans(spanIds);
  }

  @Override
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // close failure is not fatal, of any kind
  public void close() {
    if (retention instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Throwable t) { // NOPMD
        // Best-effort — close failure is not fatal
      }
    }
  }
}
