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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class NarrativeTraceBeanListenerTest {
    private val context: NarrativeContext = ThreadLocalNarrativeContext()

    @Test
    fun wrapsInterfaceBasedBeanWithProxy() {
        val bean = DefaultGreetingService()
        val result =
            NarrativeTraceBeanListener.wrapIfEligible(
                bean,
                listOf("ai.narrativetrace.micronaut"),
                context,
            )
        assertThat(Proxy.isProxyClass(result.javaClass)).isTrue()
    }

    @Test
    fun skipsBeanWithoutInterfaces() {
        val bean = NoInterfaceBean()
        val result =
            NarrativeTraceBeanListener.wrapIfEligible(
                bean,
                listOf("ai.narrativetrace.micronaut"),
                context,
            )
        assertThat(result).isSameAs(bean)
    }

    @Test
    fun packageFilterControlsWhichBeansAreWrapped() {
        val bean = DefaultGreetingService()
        val result =
            NarrativeTraceBeanListener.wrapIfEligible(
                bean,
                listOf("com.other.package"),
                context,
            )
        assertThat(result).isSameAs(bean)
    }

    @Test
    fun wrapsMultiInterfaceBeans() {
        val bean = MultiServiceImpl()
        val result =
            NarrativeTraceBeanListener.wrapIfEligible(
                bean,
                listOf("ai.narrativetrace.micronaut"),
                context,
            )
        assertThat(Proxy.isProxyClass(result.javaClass)).isTrue()
        assertThat(result).isInstanceOf(GreetingService::class.java)
        assertThat(result).isInstanceOf(FarewellService::class.java)
    }

    @Test
    fun emptyBasePackagesWrapsNothing() {
        val bean = DefaultGreetingService()
        val result =
            NarrativeTraceBeanListener.wrapIfEligible(
                bean,
                emptyList(),
                context,
            )
        assertThat(result).isSameAs(bean)
    }

    @Test
    fun skipsBeanWithNonMatchingInterfacePackages() {
        val bean = ExternalInterfaceImpl()
        val result =
            NarrativeTraceBeanListener.wrapIfEligible(
                bean,
                listOf("ai.narrativetrace.micronaut"),
                context,
            )
        // Bean class matches package, but its interface (java.lang.Runnable) doesn't
        assertThat(result).isSameAs(bean)
    }
}

// --- Test fixtures ---

interface GreetingService {
    fun greet(name: String): String
}

interface FarewellService {
    fun farewell(name: String): String
}

class DefaultGreetingService : GreetingService {
    override fun greet(name: String): String = "Hello, $name"
}

class MultiServiceImpl :
    GreetingService,
    FarewellService {
    override fun greet(name: String): String = "Hello, $name"

    override fun farewell(name: String): String = "Goodbye, $name"
}

class NoInterfaceBean {
    fun doWork(): String = "done"
}

class ExternalInterfaceImpl : Runnable {
    override fun run() {}
}
