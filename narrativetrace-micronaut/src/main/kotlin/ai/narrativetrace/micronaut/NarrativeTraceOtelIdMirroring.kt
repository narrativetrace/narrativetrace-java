/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut

import ai.narrativetrace.opentelemetry.NarrativeTraceAutoConfiguration
import ai.narrativetrace.opentelemetry.NarrativeTraceIdGenerator
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.tracing.opentelemetry.OpenTelemetryBuilderCustomizer
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder
import jakarta.inject.Singleton

/**
 * Installs [NarrativeTraceIdGenerator] on the SDK Micronaut's OpenTelemetry tracing builds, so
 * NarrativeTrace's spans carry their NarrativeTrace ids — on by default.
 *
 * INTENT: Micronaut's `DefaultOpenTelemetryFactory` builds its SDK through OpenTelemetry
 * autoconfigure and exposes no tracer-provider-builder bean; [OpenTelemetryBuilderCustomizer] is
 * the hook it offers, and the one used here. Present only when Micronaut's OpenTelemetry tracing
 * and `narrativetrace-opentelemetry` are both on the classpath.
 *
 * @howToUse Opt out with `narrativetrace.otel.mirror-ids: false` in Micronaut configuration (also
 * read from `-Dnarrativetrace.otel.mirror-ids=false` or `NARRATIVETRACE_OTEL_MIRROR_IDS=false`).
 * Opting out here also tells the autoconfigure service entry of `narrativetrace-opentelemetry`,
 * which would otherwise install the generator on the same SDK.
 *
 * @edgeCase An application-defined `IdGenerator` bean keeps serving the application's own spans:
 * the generator wraps whatever generator the builder already holds.
 */
@Singleton
@Requires(
    classes = [
        OpenTelemetryBuilderCustomizer::class,
        NarrativeTraceIdGenerator::class,
        AutoConfiguredOpenTelemetrySdkBuilder::class,
    ],
)
class NarrativeTraceOtelIdMirroring(
    @Value("\${narrativetrace.otel.mirror-ids:true}") private val enabled: Boolean,
) : OpenTelemetryBuilderCustomizer {
    override fun configure(builder: AutoConfiguredOpenTelemetrySdkBuilder) {
        if (enabled) {
            builder.addTracerProviderCustomizer { tracerProvider, _ ->
                NarrativeTraceIdGenerator.installOn(tracerProvider)
            }
        } else {
            builder.addPropertiesSupplier {
                mapOf(NarrativeTraceAutoConfiguration.MIRROR_IDS_CONFIG_KEY to "false")
            }
        }
    }
}
