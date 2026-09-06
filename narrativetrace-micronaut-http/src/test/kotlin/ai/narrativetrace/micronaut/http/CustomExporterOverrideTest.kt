/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut.http

import ai.narrativetrace.api.export.RequestContext
import ai.narrativetrace.api.export.TraceExporter
import ai.narrativetrace.api.tree.TraceTree
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MicronautTest(environments = ["custom-exporter"])
class CustomExporterOverrideTest {
    @Inject
    lateinit var exporter: TraceExporter

    @Test
    fun customExporterReplacesDefault() {
        assertThat(exporter).isSameAs(CUSTOM_EXPORTER)
    }

    @Requires(env = ["custom-exporter"])
    @Factory
    class TestExporterFactory {
        @Bean
        @Singleton
        fun customExporter(): TraceExporter = CUSTOM_EXPORTER
    }

    companion object {
        val CUSTOM_EXPORTER = TraceExporter { _: TraceTree, _: RequestContext -> }
    }
}
