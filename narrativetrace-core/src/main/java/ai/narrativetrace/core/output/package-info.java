/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Test-oriented output helpers built on top of the core tree and renderer APIs.
 *
 * <p>{@link ai.narrativetrace.core.output.TraceTestSupport} centralizes the file-writing behavior
 * shared by the JUnit integrations: it renders traces in the requested format, writes companion
 * Mermaid and JSON files for Markdown output, and emits a console copy of the text trace.
 *
 * <p>{@link ai.narrativetrace.core.output.ScenarioFramer} derives human-readable scenario titles
 * from test names, {@link ai.narrativetrace.core.output.OutputDirectoryResolver} standardizes the
 * on-disk layout, and {@link ai.narrativetrace.core.output.TemplateWarningCollector} reports
 * unresolved annotation placeholders that survived template resolution.
 */
package ai.narrativetrace.core.output;
