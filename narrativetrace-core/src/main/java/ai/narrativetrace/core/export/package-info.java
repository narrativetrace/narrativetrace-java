/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Export helpers for request-boundary integrations.
 *
 * <p>{@link ai.narrativetrace.api.export.TraceExporter} is the small SPI used by integrations such
 * as servlet filters to hand off a completed trace together with request outcome metadata. {@link
 * ai.narrativetrace.api.export.RequestContext} carries only fields known after the request
 * finishes, while per-span request and user fields live on {@code SpanContext}.
 *
 * <p>{@link ai.narrativetrace.core.export.JsonExporter} turns a trace tree into a flat event stream
 * JSON document suitable for file output or downstream ingestion.
 */
package ai.narrativetrace.core.export;
