/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.http

import ai.narrativetrace.api.export.TraceExporter
import ai.narrativetrace.servlet.Slf4jTraceExporter
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MicronautTest
class NarrativeTraceHttpFactoryTest {
    @Inject
    lateinit var exporter: TraceExporter

    @Inject
    lateinit var filter: NarrativeTraceHttpFilter

    @Test
    fun factoryProvidesDefaultExporter() {
        assertThat(exporter).isInstanceOf(Slf4jTraceExporter::class.java)
    }

    @Test
    fun factoryProvidesFilterBean() {
        assertThat(filter).isNotNull()
    }
}
