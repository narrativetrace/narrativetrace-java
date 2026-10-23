/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.opentelemetry;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.function.UnaryOperator;

/**
 * OpenTelemetry autoconfigure entry that installs {@link NarrativeTraceIdGenerator} by default.
 *
 * <p>INTENT: Applications that build their SDK with {@code
 * opentelemetry-sdk-extension-autoconfigure} — directly, or through a framework built on it such as
 * Micronaut's OpenTelemetry tracing — get mirrored ids with no code. Registered in {@code
 * META-INF/services}; ignored when autoconfigure is not on the classpath.
 *
 * <p><b>@edgeCase</b> Does not reach an application traced by the OpenTelemetry Java agent: the
 * agent's SDK never reads service entries from the application classpath. Those applications run
 * without mirroring (see {@link NarrativeTraceIdGenerator}).
 *
 * <p><b>@howToUse</b> Opt out with the system property {@code narrativetrace.otel.mirrorIds=false},
 * the environment variable {@code NARRATIVETRACE_OTEL_MIRROR_IDS=false}, or the autoconfigure
 * property {@code narrativetrace.otel.mirror.ids=false}.
 */
public final class NarrativeTraceAutoConfiguration implements AutoConfigurationCustomizerProvider {

  /** System property that turns mirroring off when set to {@code false}. */
  public static final String MIRROR_IDS_PROPERTY = "narrativetrace.otel.mirrorIds";

  /** Environment variable that turns mirroring off when set to {@code false}. */
  public static final String MIRROR_IDS_ENV = "NARRATIVETRACE_OTEL_MIRROR_IDS";

  /** Autoconfigure property key (its normalised form) that turns mirroring off. */
  public static final String MIRROR_IDS_CONFIG_KEY = "narrativetrace.otel.mirror.ids";

  @Override
  public void customize(AutoConfigurationCustomizer customizer) {
    customizer.addTracerProviderCustomizer(
        (builder, config) ->
            mirrorIdsEnabled(config, System::getProperty, System::getenv)
                ? NarrativeTraceIdGenerator.installOn(builder)
                : builder);
  }

  static boolean mirrorIdsEnabled(
      ConfigProperties config, UnaryOperator<String> systemProperty, UnaryOperator<String> env) {
    if (isFalse(systemProperty.apply(MIRROR_IDS_PROPERTY))) {
      return false;
    }
    if (isFalse(env.apply(MIRROR_IDS_ENV))) {
      return false;
    }
    return config == null || config.getBoolean(MIRROR_IDS_CONFIG_KEY, true);
  }

  private static boolean isFalse(String value) {
    return value != null && "false".equalsIgnoreCase(value.trim());
  }
}
