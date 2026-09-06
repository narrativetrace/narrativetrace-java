/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut

import io.micronaut.context.annotation.ConfigurationProperties

/**
 * Configuration properties for NarrativeTrace Micronaut integration.
 *
 * INTENT: Binds `narrativetrace.*` from `application.yml` to typed fields
 * used by the factory and bean listener.
 *
 * @llmNote Micronaut mutates `var` fields during binding — all properties must be `var`.
 */
@ConfigurationProperties("narrativetrace")
class NarrativeTraceProperties {
    var basePackages: List<String> = emptyList()
    var loggerName: String = "narrativetrace"
    var serviceName: String = ""
    var serviceVersion: String = ""
    var environment: String = ""
}
