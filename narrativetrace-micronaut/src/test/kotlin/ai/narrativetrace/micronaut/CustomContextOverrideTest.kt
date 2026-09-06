/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut

import ai.narrativetrace.core.context.NarrativeContext
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MicronautTest(environments = ["custom-context"])
class CustomContextOverrideTest {
    @Inject
    lateinit var context: NarrativeContext

    @Test
    fun userDefinedContextBeanOverridesDefault() {
        assertThat(context).isSameAs(CUSTOM_CONTEXT)
    }

    @Requires(env = ["custom-context"])
    @Factory
    class TestContextFactory {
        @Bean
        @Singleton
        fun customContext(): NarrativeContext = CUSTOM_CONTEXT
    }

    companion object {
        val CUSTOM_CONTEXT: NarrativeContext = ThreadLocalNarrativeContext()
    }
}
