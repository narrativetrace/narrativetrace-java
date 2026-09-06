/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Micrometer context-propagation bridge for NarrativeTrace snapshots.
 *
 * <p>{@link ai.narrativetrace.micrometer.NarrativeTraceThreadLocalAccessor} exposes the current
 * NarrativeTrace snapshot through Micrometer's context-propagation SPI so frameworks such as Spring
 * Boot and Reactor can restore trace lineage on another thread.
 *
 * <p>INTENT: Use this module when Micrometer context propagation is already part of your runtime.
 */
package ai.narrativetrace.micrometer;
