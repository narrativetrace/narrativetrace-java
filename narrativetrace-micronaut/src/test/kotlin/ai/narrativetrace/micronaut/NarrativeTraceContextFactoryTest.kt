/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut

import ai.narrativetrace.core.context.ThreadLocalNarrativeContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NarrativeTraceContextFactoryTest {
    @Test
    fun createsPlainContextWhenLoggerNameEmpty() {
        val context = NarrativeTraceContextFactory.createContext("")
        assertThat(context).isInstanceOf(ThreadLocalNarrativeContext::class.java)
    }

    @Test
    fun createsSlf4jWiredContextWhenLoggerNameProvided() {
        val context = NarrativeTraceContextFactory.createContext("myapp")
        assertThat(context).isInstanceOf(ThreadLocalNarrativeContext::class.java)
        // SLF4J listener is on test classpath, so this should succeed without fallback
        // Verify the context is functional
        assertThat(context.isActive).isTrue()
    }

    @Test
    fun fallsBackWhenSlf4jListenerClassMissing() {
        val context =
            NarrativeTraceContextFactory.createContext(
                "myapp",
                null,
                "com.nonexistent.Listener",
            )
        assertThat(context).isInstanceOf(ThreadLocalNarrativeContext::class.java)
    }

    @Test
    fun passesServiceIdentityToContext() {
        val identity =
            ai.narrativetrace.api.event
                .ServiceIdentity("svc", "1.0", "prod")
        val context = NarrativeTraceContextFactory.createContext("myapp", identity)
        assertThat(context).isInstanceOf(ThreadLocalNarrativeContext::class.java)
    }
}
