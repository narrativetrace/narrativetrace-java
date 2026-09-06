/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MicronautTest
@Property(name = "narrativetrace.logger-name", value = "myapp.trace")
@Property(name = "narrativetrace.base-packages", value = "com.myapp")
@Property(name = "narrativetrace.service-name", value = "order-service")
@Property(name = "narrativetrace.service-version", value = "2.0")
@Property(name = "narrativetrace.environment", value = "staging")
class PropertiesBindingTest {
    @Inject
    lateinit var props: NarrativeTraceProperties

    @Test
    fun loggerNameConfigurableViaProperties() {
        assertThat(props.loggerName).isEqualTo("myapp.trace")
    }

    @Test
    fun basePackagesConfigurableViaProperties() {
        assertThat(props.basePackages).containsExactly("com.myapp")
    }

    @Test
    fun serviceIdentityFieldsConfigurableViaProperties() {
        assertThat(props.serviceName).isEqualTo("order-service")
        assertThat(props.serviceVersion).isEqualTo("2.0")
        assertThat(props.environment).isEqualTo("staging")
    }
}
