/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut

import ai.narrativetrace.api.event.ServiceIdentity
import ai.narrativetrace.core.context.NarrativeContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Secondary
import jakarta.inject.Singleton

/**
 * Micronaut factory that provides the default [NarrativeContext].
 *
 * INTENT: Auto-discovered on classpath — no enable annotation needed. The `@Secondary` context
 * bean lets users override it by declaring their own `@Bean NarrativeContext`.
 */
@Factory
class NarrativeTraceFactory {
    @Bean
    @Singleton
    @Secondary
    fun narrativeContext(props: NarrativeTraceProperties): NarrativeContext {
        val identity = serviceIdentity(props)
        return NarrativeTraceContextFactory.createContext(props.loggerName, identity)
    }

    private fun serviceIdentity(props: NarrativeTraceProperties): ServiceIdentity? {
        if (props.serviceName.isEmpty() &&
            props.serviceVersion.isEmpty() &&
            props.environment.isEmpty()
        ) {
            return null
        }
        return ServiceIdentity(props.serviceName, props.serviceVersion, props.environment)
    }
}
