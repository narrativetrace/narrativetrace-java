/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Servlet-container integration for request-scoped tracing without Spring dependencies.
 *
 * <p>{@link ai.narrativetrace.servlet.NarrativeTraceFilter} manages the request lifecycle around a
 * shared {@code NarrativeContext}: reset, stamp request and user metadata, run the chain, capture
 * the tree, export it, and reset again.
 *
 * <p>INTENT: Use this module in plain servlet applications or as the lower layer under the Spring
 * Web auto-configuration module.
 */
package ai.narrativetrace.servlet;
