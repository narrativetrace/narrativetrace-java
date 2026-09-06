/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Renderers and rendering utilities for turning trace trees into strings.
 *
 * <p>{@link ai.narrativetrace.api.render.NarrativeRenderer} is the common functional interface used
 * by built-in renderers and by adapters from other modules. The core module ships Markdown,
 * plain-text, and prose renderers, plus helper types for Markdown frontmatter and trace metadata.
 *
 * <p>{@link ai.narrativetrace.core.render.ValueRenderer} sits one layer earlier in the pipeline: it
 * eagerly converts live argument and return objects into strings at capture time, with cycle
 * detection, record and POJO support, and custom summaries via {@code @NarrativeSummary}.
 */
package ai.narrativetrace.core.render;
