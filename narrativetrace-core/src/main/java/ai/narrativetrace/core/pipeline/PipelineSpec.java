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
import java.util.List;
import java.util.function.Consumer;

/**
 * Everything a pipeline topology needs to compose itself.
 *
 * <p>INTENT: The single argument to {@link EventPipelineFactory#create}, so adding a composition
 * input later is an additive change to one record rather than a signature break for every strategy
 * implementation.
 *
 * <p><b>@llmNote</b> A factory decides <em>where</em> to attach the listeners it is handed — inline
 * beside a durable write, on a ring consumer, on a queue tailer. That placement is the topology's
 * choice; the listener contract does not change with it.
 *
 * @param durableListener The synchronous durable listener (typically SLF4J narration) when one is
 *     configured, otherwise {@code null}. A topology that relinquishes the durable path ignores it,
 *     but must say so explicitly rather than dropping it silently.
 * @param listeners Discovered additive listeners to place somewhere in the topology. Never {@code
 *     null}; possibly empty.
 * @param settings Configuration for this strategy's own namespace.
 */
public record PipelineSpec(
    Consumer<TraceEvent> durableListener, List<TraceEventListener> listeners, Settings settings) {

  /** Rejects the two components a factory may never receive as {@code null}. */
  public PipelineSpec {
    if (listeners == null) {
      throw new IllegalArgumentException("listeners must not be null");
    }
    if (settings == null) {
      throw new IllegalArgumentException("settings must not be null");
    }
    listeners = List.copyOf(listeners);
  }

  /**
   * Configuration lookup scoped to one strategy's namespace.
   *
   * <p>INTENT: Keeps strategy-specific keys out of core. A factory named {@code chronicle} asks for
   * {@code "path"} and the implementation resolves {@code narrativetrace.pipeline.chronicle.path}
   * through the ordinary configuration chain — core never learns what keys a strategy has.
   */
  @FunctionalInterface
  public interface Settings {

    /**
     * Resolves one key within this strategy's namespace.
     *
     * @param key the namespace-relative key, e.g. {@code "ringSize"}
     * @param defaultValue value returned when the key is not configured
     * @return the configured value, or {@code defaultValue}
     */
    String get(String key, String defaultValue);
  }
}
