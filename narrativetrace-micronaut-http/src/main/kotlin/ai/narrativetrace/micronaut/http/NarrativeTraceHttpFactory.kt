/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.http

import ai.narrativetrace.api.export.TraceExporter
import ai.narrativetrace.micronaut.NarrativeTraceProperties
import ai.narrativetrace.servlet.Slf4jTraceExporter
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Secondary
import jakarta.inject.Singleton

/**
 * Micronaut factory that provides the [TraceExporter] for the HTTP filter.
 *
 * INTENT: Provides a default [Slf4jTraceExporter] when no custom exporter bean exists.
 * The `@Secondary` annotation lets users override by declaring their own `@Bean TraceExporter`.
 */
@Factory
class NarrativeTraceHttpFactory {
    @Bean
    @Singleton
    @Secondary
    fun traceExporter(props: NarrativeTraceProperties): TraceExporter {
        val loggerName = props.loggerName
        return if (loggerName.isNotEmpty()) {
            Slf4jTraceExporter("$loggerName.export")
        } else {
            Slf4jTraceExporter()
        }
    }
}
