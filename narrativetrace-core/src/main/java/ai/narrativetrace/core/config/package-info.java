/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Runtime configuration for capture level and property-based overrides.
 *
 * <p>{@link ai.narrativetrace.core.config.NarrativeTraceConfig} carries the active {@link
 * ai.narrativetrace.api.config.TracingLevel} and exposes a volatile setter so a shared config can
 * be changed at runtime. The default level is {@code DETAIL}.
 *
 * <p>{@link ai.narrativetrace.core.config.ConfigResolver} loads {@code narrativetrace.properties}
 * from the classpath and lets JVM system properties override individual keys. Duplicate property
 * files are treated as a configuration error.
 */
package ai.narrativetrace.core.config;
