/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * OpenTelemetry export and live-listener adapters for NarrativeTrace.
 *
 * <p>{@link ai.narrativetrace.opentelemetry.TraceSpanExporter} converts completed trace trees into
 * OpenTelemetry spans after capture, while {@link
 * ai.narrativetrace.opentelemetry.OtelTraceEventListener} creates spans live from the core event
 * stream.
 *
 * <p>INTENT: Use this module when NarrativeTrace data needs to join an existing OpenTelemetry
 * pipeline rather than only producing local files or logs.
 */
package ai.narrativetrace.opentelemetry;
