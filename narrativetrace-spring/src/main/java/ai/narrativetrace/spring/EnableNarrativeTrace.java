/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Imports Spring infrastructure for automatic proxy-based tracing.
 *
 * <p>INTENT: Apply this to one configuration class to register the shared trace context and the
 * bean post processor that wraps matching beans.
 *
 * <pre>{@code
 * @Configuration
 * @EnableNarrativeTrace(basePackages = "com.example.service")
 * public class AppConfig { }
 * }</pre>
 *
 * <p><b>@llmNote</b> The bean post processor runs at {@code HIGHEST_PRECEDENCE} so the tracing
 * proxy becomes the innermost wrapper. With {@code @EnableAsync}, the async proxy stays outside and
 * trace capture happens on the async thread.
 *
 * <p><b>@edgeCase</b> If no {@code basePackages} are declared, the registrar defaults to the
 * package of the importing configuration class, not the entire application.
 *
 * @see NarrativeTraceConfiguration
 * @see NarrativeTraceBeanPostProcessor
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({NarrativeTraceRegistrar.class, NarrativeTraceConfiguration.class})
public @interface EnableNarrativeTrace {
  /**
   * Package prefixes used to decide which beans and interfaces are wrapped.
   *
   * @return Package prefixes to trace. When empty, the importer package is used as the default.
   */
  String[] basePackages() default {};

  /**
   * Base SLF4J logger name for synchronous event logging.
   *
   * @return Logger name used by the optional SLF4J event listener. Empty disables that listener.
   */
  String loggerName() default "narrativetrace";

  /**
   * Logical service name stamped onto every created {@code SpanContext}.
   *
   * @return Service name, or empty string to omit it.
   */
  String serviceName() default "";

  /**
   * Service version stamped onto every created {@code SpanContext}.
   *
   * @return Service version, or empty string to omit it.
   */
  String serviceVersion() default "";

  /**
   * Deployment environment stamped onto every created {@code SpanContext}.
   *
   * @return Environment label, or empty string to omit it.
   */
  String environment() default "";
}
