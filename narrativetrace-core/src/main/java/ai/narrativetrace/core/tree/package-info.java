/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Immutable trace tree view built from append-only events.
 *
 * <p>{@link ai.narrativetrace.core.tree.TraceTreeBuilder} assembles {@link
 * ai.narrativetrace.api.event.TraceEvent TraceEvent} sequences into {@link
 * ai.narrativetrace.api.tree.TraceTree} instances. The builder also applies the selected {@code
 * TracingLevel}: {@code ERRORS} retains only error and incomplete paths, while {@code SUMMARY}
 * keeps roots and leaves.
 *
 * <p>{@link ai.narrativetrace.core.tree.DefaultTraceTree} is the standard immutable implementation
 * returned by the core context.
 */
package ai.narrativetrace.core.tree;
