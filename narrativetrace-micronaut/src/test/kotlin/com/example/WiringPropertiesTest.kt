/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example

import io.micronaut.context.ApplicationContext
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.Properties

interface Greeter {
    fun greet(name: String): String
}

@Singleton
class DefaultGreeter : Greeter {
    override fun greet(name: String): String = "Hello, $name!"
}

/**
 * The framework table's Micronaut row embeds src/test/resources/wiring/application.properties
 * whole as its wiring snippet — the doctor's config.micronaut-base-packages fix line and
 * llms-full.md's integration table. This runs exactly that file: a bean under the base package
 * comes back traced. The file lives under wiring/, not at the resource root, so it never becomes
 * every other test's configuration by accident.
 */
class WiringPropertiesTest {
    @Test
    fun theSnippetsBasePackageGetsItsBeansTraced() {
        val properties = Properties()
        javaClass.getResourceAsStream("/wiring/application.properties").use { properties.load(it) }
        val settings: Map<String, Any> = properties.stringPropertyNames().associateWith { properties.getProperty(it) }

        ApplicationContext.run(settings).use { ctx ->
            val greeter = ctx.getBean(Greeter::class.java)
            assertThat(Proxy.isProxyClass(greeter.javaClass)).isTrue()
            assertThat(greeter.greet("Alice")).isEqualTo("Hello, Alice!")
        }
    }
}
