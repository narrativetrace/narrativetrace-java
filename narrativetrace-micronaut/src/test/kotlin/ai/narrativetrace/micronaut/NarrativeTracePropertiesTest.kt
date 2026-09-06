/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MicronautTest
class NarrativeTracePropertiesTest {
    @Inject
    lateinit var properties: NarrativeTraceProperties

    @Test
    fun defaultsToEmptyBasePackages() {
        assertThat(properties.basePackages).isEmpty()
    }

    @Test
    fun defaultsLoggerNameToNarrativetrace() {
        assertThat(properties.loggerName).isEqualTo("narrativetrace")
    }

    @Test
    fun defaultsServiceIdentityFieldsToEmpty() {
        assertThat(properties.serviceName).isEmpty()
        assertThat(properties.serviceVersion).isEmpty()
        assertThat(properties.environment).isEmpty()
    }
}
