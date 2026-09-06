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
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MicronautTest
class NarrativeTraceFactoryTest {
    @Inject
    lateinit var context: NarrativeContext

    @Test
    fun factoryBeanProvidesNarrativeContext() {
        assertThat(context).isInstanceOf(ThreadLocalNarrativeContext::class.java)
    }

    @Test
    fun contextIsActive() {
        assertThat(context.isActive).isTrue()
    }
}
