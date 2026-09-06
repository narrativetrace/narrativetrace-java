/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

/**
 * Replacement extension point supplying an alternative pipeline topology.
 *
 * <p>INTENT: The seam for changing how events travel — a ring buffer, a memory-mapped queue, a
 * chain of both — behind the unchanged {@link EventPipeline} contract. Unlike the additive
 * extension points, exactly one factory can hold the slot.
 *
 * <p><b>@llmNote</b> Classpath presence must NEVER activate a factory. Activation is a
 * configuration act: the deployment names a strategy and only then is a factory looked up. This is
 * deliberate — durability is a correctness property, and a jar arriving on the classpath must not
 * change it. A named strategy with no matching factory fails initialization rather than falling
 * back, because silently running a different durability topology than the one configured is worse
 * than not starting.
 *
 * @see PipelineSpec
 */
public interface EventPipelineFactory {

  /**
   * The name this factory answers to in configuration.
   *
   * @return A stable, lowercase strategy name, e.g. {@code "chronicle"}. Never {@code null}.
   */
  String name();

  /**
   * Builds the pipeline for this strategy.
   *
   * @param spec the durable listener, discovered listeners, and namespaced settings to compose from
   * @return A ready pipeline. Never {@code null}.
   */
  EventPipeline create(PipelineSpec spec);
}
