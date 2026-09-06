/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.spi.TraceEventListener;
import java.util.function.Consumer;

/**
 * The one place an observability callback is invoked.
 *
 * <p>INTENT: Every listener, consumer, observer and extension the pipeline calls is somebody else's
 * code running on the application's thread. The contract is that its failure is never the
 * application's failure, and that contract is worth exactly one implementation — five call sites
 * each writing their own {@code try}/{@code catch} is five chances to catch the wrong thing, which
 * is how {@code AssertionError} escaped {@code publish} until 2026-09-01.
 *
 * <p><b>@llmNote</b> {@link Throwable}, not {@link Exception}. The realistic failure modes of a
 * discovered extension are an assertion in a listener's own test-enabled build, a {@code
 * LinkageError} from a shaded or mismatched dependency, and an {@code ExceptionInInitializerError}
 * from a static block — none of which is an {@code Exception}.
 *
 * <p><b>@edgeCase</b> Each entry point takes the callback and its argument rather than a {@code
 * Runnable} closure, so a guarded call allocates nothing. These run on the publish path.
 */
final class TraceBoundary {

  private TraceBoundary() {}

  /**
   * Hands one event to one consumer.
   *
   * @param consumer the consumer to feed; may be {@code null}, in which case nothing happens
   * @param event the event to deliver
   * @return what the consumer threw, or {@code null} when it completed
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // observability failure is never app failure
  static Throwable deliver(Consumer<TraceEvent> consumer, TraceEvent event) {
    if (consumer == null) {
      return null;
    }
    try {
      consumer.accept(event);
      return null;
    } catch (Throwable t) { // NOPMD
      return t;
    }
  }

  /**
   * Hands one event to one discovered listener.
   *
   * @param listener the listener to notify
   * @param event the event to deliver
   * @return what the listener threw, or {@code null} when it completed
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // observability failure is never app failure
  static Throwable notify(TraceEventListener listener, TraceEvent event) {
    try {
      listener.onEvent(event);
      return null;
    } catch (Throwable t) { // NOPMD
      return t;
    }
  }

  /**
   * Runs one observability side effect — a close, a watchdog callback, a lifecycle notification.
   *
   * @param action the side effect to run
   * @return what it threw, or {@code null} when it completed
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // observability failure is never app failure
  static Throwable run(Runnable action) {
    try {
      action.run();
      return null;
    } catch (Throwable t) { // NOPMD
      return t;
    }
  }
}
