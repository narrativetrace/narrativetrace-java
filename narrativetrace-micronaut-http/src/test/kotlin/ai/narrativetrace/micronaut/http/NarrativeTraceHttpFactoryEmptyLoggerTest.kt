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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NarrativeTraceHttpFactoryEmptyLoggerTest {
    @Test
    fun emptyLoggerNameUsesDefaultExporter() {
        val factory = NarrativeTraceHttpFactory()
        val props = NarrativeTraceProperties()
        props.loggerName = ""
        val exporter: TraceExporter = factory.traceExporter(props)
        assertThat(exporter).isInstanceOf(Slf4jTraceExporter::class.java)
    }

    @Test
    fun nonEmptyLoggerNameUsesNamedExporter() {
        val factory = NarrativeTraceHttpFactory()
        val props = NarrativeTraceProperties()
        props.loggerName = "myapp"
        val exporter: TraceExporter = factory.traceExporter(props)
        assertThat(exporter).isInstanceOf(Slf4jTraceExporter::class.java)
    }
}
