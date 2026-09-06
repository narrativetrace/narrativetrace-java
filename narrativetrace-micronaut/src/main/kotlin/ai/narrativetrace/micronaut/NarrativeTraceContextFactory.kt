/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut

import ai.narrativetrace.api.event.ServiceIdentity
import ai.narrativetrace.api.event.TraceEvent
import ai.narrativetrace.core.config.NarrativeTraceConfig
import ai.narrativetrace.core.context.NarrativeContext
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext
import ai.narrativetrace.core.pipeline.DualPathPipeline
import java.util.function.Consumer

/**
 * Internal factory that creates a [NarrativeContext] with optional SLF4J listener.
 *
 * INTENT: Mirror the Spring `NarrativeTraceContextFactory` — loads the SLF4J event listener
 * via reflection so the slf4j module remains an optional compile-time dependency.
 *
 * @edgeCase Falls back to a plain [ThreadLocalNarrativeContext] when the listener class
 * is unavailable or construction fails.
 *
 * @edgeCase Catches [Throwable], not [Exception], and that breadth is the contract rather than
 * caution. Probing the classpath for an optional jar raises errors that are not exceptions: a
 * shaded or version-mismatched SLF4J gives a [LinkageError], a listener whose static initialiser
 * throws gives an [ExceptionInInitializerError], and a truncated or incompatible class file gives
 * a [NoClassDefFoundError]. This runs while Micronaut builds a bean, so anything escaping fails
 * application startup — an optional observability jar has no business stopping an application from
 * starting. Core's `PipelineBootstrap` has caught [Throwable] here since an earlier bug hunt found
 * the gap; this factory does the same job by the same mechanism and was missed.
 */
internal object NarrativeTraceContextFactory {
    private const val SLF4J_LISTENER_CLASS = "ai.narrativetrace.slf4j.Slf4jTraceEventListener"

    fun createContext(loggerName: String): NarrativeContext = createContext(loggerName, null, SLF4J_LISTENER_CLASS)

    fun createContext(
        loggerName: String,
        serviceIdentity: ServiceIdentity?,
    ): NarrativeContext = createContext(loggerName, serviceIdentity, SLF4J_LISTENER_CLASS)

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    internal fun createContext(
        loggerName: String,
        serviceIdentity: ServiceIdentity?,
        listenerClassName: String,
    ): NarrativeContext {
        if (loggerName.isEmpty()) {
            return ThreadLocalNarrativeContext(
                NarrativeTraceConfig(),
                DualPathPipeline(null),
                serviceIdentity,
            )
        }
        return try {
            val clazz = Class.forName(listenerClassName)
            val constructor = clazz.getConstructor(String::class.java)

            @Suppress("UNCHECKED_CAST")
            val listener = constructor.newInstance(loggerName) as Consumer<TraceEvent>
            ThreadLocalNarrativeContext(
                NarrativeTraceConfig(),
                DualPathPipeline(listener),
                serviceIdentity,
            )
        } catch (_: Throwable) {
            ThreadLocalNarrativeContext(
                NarrativeTraceConfig(),
                DualPathPipeline(null),
                serviceIdentity,
            )
        }
    }
}
