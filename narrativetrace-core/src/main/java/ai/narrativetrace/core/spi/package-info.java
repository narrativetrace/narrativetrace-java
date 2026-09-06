/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Extension points third-party modules implement and the core discovers.
 *
 * <p>This package holds the additive extension points — {@link
 * ai.narrativetrace.api.spi.TraceEventListener} for the live event stream and {@link
 * ai.narrativetrace.api.spi.ReportContributor} for end-of-run output — together with the {@link
 * ai.narrativetrace.core.spi.ExtensionRegistry} that discovers them through {@link
 * java.util.ServiceLoader}. Both are active by classpath presence, many can coexist, and one
 * failing never affects the others or the host application.
 *
 * <p>INTENT: Keep the seams generic and audience-facing. Nothing here names a tier, a product, or a
 * particular implementation, so the same contracts serve first-party modules, commercial add-ons,
 * and community extensions alike.
 *
 * <p><b>@llmNote</b> The <em>replacement</em> extension point lives next to the abstraction it
 * replaces, in {@code ai.narrativetrace.core.pipeline}: a factory returning an {@code
 * EventPipeline} would otherwise make these two packages mutually dependent. Additive extensions
 * are discovered and activated by presence; a replacement topology is only ever looked up once
 * configuration names it.
 */
package ai.narrativetrace.core.spi;
