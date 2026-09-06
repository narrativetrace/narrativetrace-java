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
import java.util.function.Consumer

/**
 * Loading the optional SLF4J listener must never fail the application it is optional for.
 *
 * INTENT: An adversarial review found this factory catching `Exception`
 * where core's `PipelineBootstrap` catches `Throwable`. A shaded or version-mismatched SLF4J, a
 * corrupted optional jar, or a listener whose static initialiser throws raises a `LinkageError` or
 * an `ExceptionInInitializerError` — neither of which is an `Exception` — and the error escaped
 * into Micronaut's bean creation, failing startup.
 *
 * @llmNote This is a MISSED INSTANCE of an earlier bug hunt's finding, whose contract is
 * that every trace boundary running on the application thread must be total, `Error` subclasses
 * included. That finding fixed `PipelineBootstrap`; this factory does the same job by the same
 * mechanism and was not looked at.
 */
class ContextFactoryTotalityTest {
    @Test
    fun aListenerWhoseStaticInitialiserThrowsAnErrorDoesNotFailStartup() {
        val context =
            NarrativeTraceContextFactory.createContext(
                "myapp",
                null,
                ThrowsLinkageErrorOnInit::class.java.name,
            )

        assertThat(context).isInstanceOf(ThreadLocalNarrativeContext::class.java)
        assertThat(context.isActive).isTrue()
    }

    @Test
    fun aListenerWhoseConstructorThrowsAnErrorDoesNotFailStartup() {
        val context =
            NarrativeTraceContextFactory.createContext(
                "myapp",
                null,
                ConstructorThrowsError::class.java.name,
            )

        assertThat(context).isInstanceOf(ThreadLocalNarrativeContext::class.java)
    }

    @Test
    fun aListenerWhoseConstructorThrowsAnExceptionDoesNotFailStartup() {
        val context =
            NarrativeTraceContextFactory.createContext(
                "myapp",
                null,
                ConstructorThrowsException::class.java.name,
            )

        assertThat(context).isInstanceOf(ThreadLocalNarrativeContext::class.java)
    }

    @Test
    fun aListenerOfTheWrongTypeDoesNotFailStartup() {
        val context =
            NarrativeTraceContextFactory.createContext(
                "myapp",
                null,
                NotAConsumer::class.java.name,
            )

        assertThat(context).isInstanceOf(ThreadLocalNarrativeContext::class.java)
    }

    @Test
    fun aListenerWithoutTheExpectedConstructorDoesNotFailStartup() {
        val context =
            NarrativeTraceContextFactory.createContext(
                "myapp",
                null,
                NoStringConstructor::class.java.name,
            )

        assertThat(context).isInstanceOf(ThreadLocalNarrativeContext::class.java)
    }

    @Test
    fun aWorkingListenerIsStillWiredIn() {
        val context =
            NarrativeTraceContextFactory.createContext(
                "myapp",
                null,
                WorkingListener::class.java.name,
            )

        assertThat(context).isInstanceOf(ThreadLocalNarrativeContext::class.java)
        assertThat(context.isActive).isTrue()
    }

    /** Static initialisation raises `LinkageError`, exactly as a shaded/mismatched jar would. */
    class ThrowsLinkageErrorOnInit(
        @Suppress("UNUSED_PARAMETER") loggerName: String,
    ) : Consumer<ai.narrativetrace.api.event.TraceEvent> {
        override fun accept(event: ai.narrativetrace.api.event.TraceEvent) = Unit

        companion object {
            init {
                throw LinkageError("boom-linkage-canary")
            }
        }
    }

    class ConstructorThrowsError(
        @Suppress("UNUSED_PARAMETER") loggerName: String,
    ) : Consumer<ai.narrativetrace.api.event.TraceEvent> {
        init {
            throw NoClassDefFoundError("boom-noclassdef-canary")
        }

        override fun accept(event: ai.narrativetrace.api.event.TraceEvent) = Unit
    }

    class ConstructorThrowsException(
        @Suppress("UNUSED_PARAMETER") loggerName: String,
    ) : Consumer<ai.narrativetrace.api.event.TraceEvent> {
        init {
            throw IllegalStateException("boom-exception-canary")
        }

        override fun accept(event: ai.narrativetrace.api.event.TraceEvent) = Unit
    }

    /** Constructible and of the right shape, but not a `Consumer` — the cast must not escape. */
    class NotAConsumer(
        @Suppress("UNUSED_PARAMETER") loggerName: String,
    )

    class NoStringConstructor : Consumer<ai.narrativetrace.api.event.TraceEvent> {
        override fun accept(event: ai.narrativetrace.api.event.TraceEvent) = Unit
    }

    class WorkingListener(
        @Suppress("UNUSED_PARAMETER") loggerName: String,
    ) : Consumer<ai.narrativetrace.api.event.TraceEvent> {
        override fun accept(event: ai.narrativetrace.api.event.TraceEvent) = Unit
    }
}
