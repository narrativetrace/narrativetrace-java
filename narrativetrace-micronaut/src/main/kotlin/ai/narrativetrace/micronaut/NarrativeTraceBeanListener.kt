/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut

import ai.narrativetrace.core.context.NarrativeContext
import ai.narrativetrace.proxy.NarrativeTraceProxy
import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import jakarta.inject.Singleton

/**
 * Micronaut bean listener that wraps eligible beans in tracing proxies.
 *
 * INTENT: Equivalent of Spring's `NarrativeTraceBeanPostProcessor`. Fires for every bean
 * creation and wraps those whose concrete class and at least one interface match the
 * configured base packages.
 *
 * @llmNote Injects [ApplicationContext] for lazy resolution of [NarrativeContext] and
 * [NarrativeTraceProperties]. Uses a [ThreadLocal] re-entrancy guard because the listener
 * fires during creation of ALL beans — including its own dependencies.
 *
 * @llmNote `BeanCreatedEventListener<Any>` fires for ALL beans — wrapping logic returns
 * fast for non-matching packages.
 *
 * @llmNote Package matching uses a dot boundary (`pkg == base || pkg.startsWith("$base.")`), the
 * same rule Spring's `NarrativeTraceBeanPostProcessor` uses. A raw prefix made base package
 * `com.acme` also match `com.acme2` and `com.acmeExtra`, which silently instruments a sibling
 * module — or a third party's code that happens to share a prefix.
 *
 * @edgeCase One rule here is deliberately *not* Spring's: this listener requires the interface's
 * package to match a base package too, where Spring wraps every non-framework interface once the
 * implementation class is in scope. Both are defensible — Spring's says "the class is mine, so
 * trace it however it is called", this one says "trace calls that cross a boundary I own on both
 * sides" — and this is the behaviour Micronaut users already have. Widening it would wrap beans
 * through third-party and framework interfaces, which needs the JDK-interface and SPI exclusions
 * Spring carries and is a change in the opposite direction from the boundary fix above. Recorded
 * as a known divergence rather than changed silently.
 */
@Singleton
class NarrativeTraceBeanListener(
    private val applicationContext: ApplicationContext,
) : BeanCreatedEventListener<Any> {
    @Volatile
    private var context: NarrativeContext? = null

    @Volatile
    private var basePackages: List<String>? = null

    private val resolving: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    override fun onCreated(event: BeanCreatedEvent<Any>): Any {
        val bean = event.bean
        if (resolving.get()) return bean

        val packages = resolveBasePackages() ?: return bean
        val ctx = resolveContext() ?: return bean
        return wrapIfEligible(bean, packages, ctx)
    }

    private fun resolveContext(): NarrativeContext? {
        var c = context
        if (c == null) {
            resolving.set(true)
            try {
                c = applicationContext.getBean(NarrativeContext::class.java)
                context = c
            } finally {
                resolving.set(false)
            }
        }
        return c
    }

    private fun resolveBasePackages(): List<String>? {
        var p = basePackages
        if (p == null) {
            resolving.set(true)
            try {
                p = applicationContext.getBean(NarrativeTraceProperties::class.java).basePackages
                basePackages = p
            } finally {
                resolving.set(false)
            }
        }
        return p
    }

    companion object {
        /**
         * Wraps a bean in a tracing proxy if its class and at least one interface
         * match the configured base packages. Returns the original bean otherwise.
         */
        internal fun wrapIfEligible(
            bean: Any,
            basePackages: List<String>,
            context: NarrativeContext,
        ): Any {
            val beanClass = bean.javaClass

            if (!shouldWrap(beanClass, basePackages)) return bean

            val interfaces = beanClass.interfaces
            if (interfaces.isEmpty()) return bean

            val tracingInterfaces = findTracingInterfaces(interfaces, basePackages)
            if (tracingInterfaces.isEmpty()) return bean

            return if (tracingInterfaces.size == 1) {
                @Suppress("UNCHECKED_CAST")
                NarrativeTraceProxy.trace(bean, tracingInterfaces[0] as Class<Any>, context)
            } else {
                NarrativeTraceProxy.trace(bean, tracingInterfaces.toTypedArray(), context)
            }
        }

        private fun shouldWrap(
            beanClass: Class<*>,
            packages: List<String>,
        ): Boolean {
            if (packages.isEmpty()) return false
            return inBasePackages(beanClass.packageName, packages)
        }

        private fun findTracingInterfaces(
            interfaces: Array<Class<*>>,
            packages: List<String>,
        ): List<Class<*>> = interfaces.filter { inBasePackages(it.packageName, packages) }

        /**
         * Whether a package is exactly a configured base package or a sub-package of one.
         *
         * A delimiter is what makes `com.acme` a parent of `com.acme.orders` and not of
         * `com.acme2`. Kept identical to Spring's `inBasePackages` so the two integrations
         * cannot drift on which beans a deployment is asking to trace.
         */
        private fun inBasePackages(
            packageName: String,
            packages: List<String>,
        ): Boolean = packages.any { packageName == it || packageName.startsWith("$it.") }
    }
}
