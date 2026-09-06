/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.spring.web;

import ai.narrativetrace.api.export.RequestContextProvider;
import ai.narrativetrace.api.export.TraceExporter;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.servlet.NarrativeTraceFilter;
import ai.narrativetrace.servlet.Slf4jTraceExporter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that wires the servlet {@link NarrativeTraceFilter}.
 *
 * <p>INTENT: Reuse this instead of constructing the filter manually in Spring applications. It
 * pulls the shared context from the container, accepts optional overrides for exporter and request
 * context provider, and otherwise builds sensible defaults.
 *
 * <p><b>@llmNote</b> The provider bean is resolved by its bound type, {@code
 * RequestContextProvider<HttpServletRequest>}. A bean declared raw, or bound to another request
 * type, is not a candidate and the filter simply runs without user context — Spring's
 * generics-aware resolution makes that silent, so declare the binding.
 *
 * <p><b>@edgeCase</b> When no {@link TraceExporter} bean is present, the default exporter logs to
 * {@code <loggerName>.export} when a base logger name exists, or to {@code narrativetrace.export}
 * otherwise.
 *
 * <p><b>@edgeCase</b> When multiple {@link TraceExporter} beans are present, {@code getIfUnique()}
 * returns {@code null} instead of throwing {@code NoUniqueBeanDefinitionException}, so the default
 * exporter is used as a safe fallback.
 */
@Configuration
public class NarrativeTraceWebConfiguration {

  /**
   * The servlet filter that opens a trace per request, wired from whatever the context supplies.
   *
   * @param context the narrative context the request's spans are captured into
   * @param exporterProvider the application's {@code TraceExporter}, if it declares one
   * @param loggerNameProvider the logger name to narrate under, if the application names one
   * @param providerProvider the application's request-context provider, if it declares one
   * @return the configured filter bean
   */
  @Bean
  public NarrativeTraceFilter narrativeTraceFilter(
      NarrativeContext context,
      ObjectProvider<TraceExporter> exporterProvider,
      @Qualifier("narrativeTraceLoggerName") ObjectProvider<String> loggerNameProvider,
      ObjectProvider<RequestContextProvider<HttpServletRequest>> providerProvider) {
    var loggerName = loggerNameProvider.getIfAvailable(() -> "");
    var exporter = exporterProvider.getIfUnique(() -> defaultExporter(loggerName));
    var provider = providerProvider.getIfAvailable();
    return new NarrativeTraceFilter(context, exporter, provider);
  }

  static Slf4jTraceExporter defaultExporter(String loggerName) {
    if (loggerName != null && !loggerName.isEmpty()) {
      return new Slf4jTraceExporter(loggerName + ".export");
    }
    return new Slf4jTraceExporter();
  }
}
