/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Template parsing for {@code @Narrated} and {@code @OnError} values.
 *
 * <p>{@link ai.narrativetrace.core.template.TemplateParser} tokenizes template strings once and
 * caches the parsed segments for reuse. It resolves simple placeholders such as {@code {orderId}}
 * and single-hop property access such as {@code {customer.name}} against a map of raw argument
 * values, preserving unresolved placeholders verbatim so callers can warn instead of failing.
 */
package ai.narrativetrace.core.template;
