/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * SLF4J bridge for trace-event logging with MDC enrichment.
 *
 * <p>{@link ai.narrativetrace.slf4j.Slf4jTraceEventListener} consumes core {@code TraceEvent}s and
 * emits log lines while populating MDC fields such as trace id and span id for the duration of each
 * log call.
 *
 * <p>INTENT: Use this as the synchronous side of a core {@code DualPathPipeline} when log output is
 * your durable, always-on observability channel.
 */
package ai.narrativetrace.slf4j;
