/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Event-pipeline infrastructure behind core trace capture.
 *
 * <p>This package owns the append-only event flow after instrumentation emits {@code TraceEvent}s
 * and before callers query stored events. It includes the main {@link
 * ai.narrativetrace.core.pipeline.EventPipeline} abstraction, the default dual-path pipeline, the
 * asynchronous buffered consumer with its bounded ring buffer and drain infrastructure, and the
 * retained event store.
 *
 * <p>INTENT: Most application code should not depend on these types directly. Reach for them when
 * you need to attach synchronous listeners or store best-effort event history.
 */
package ai.narrativetrace.core.pipeline;
