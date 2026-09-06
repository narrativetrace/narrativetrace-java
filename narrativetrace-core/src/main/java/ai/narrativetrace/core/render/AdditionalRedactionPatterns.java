/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import java.util.Arrays;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Reads the operator-supplied deny-list additions from the JVM's system properties or environment.
 *
 * <p>INTENT: {@link RedactionPolicy#ofPatterns} <em>replaces</em> the built-in vocabulary, which is
 * the wrong tool for "our DTOs also carry a {@code betalingskort} field": taking it means giving up
 * every default the library ships, and a team that does so silently loses the next release's new
 * words. This is the additive half — whatever the application asked for, plus whatever the operator
 * running it asked for.
 *
 * <p><b>@llmNote</b> Deliberately not routed through {@code ConfigResolver}. That resolver reads a
 * classpath {@code narrativetrace.properties}, and the point of this knob is that the person
 * deploying the artifact can widen redaction <em>without</em> rebuilding it — so the lookup is the
 * system property first, then the environment variable, and nothing else.
 *
 * <p><b>@edgeCase</b> Additions are substring patterns, matched exactly as the built-in substring
 * vocabulary is. A short addition therefore carries the {@code pan} trap with it: adding {@code id}
 * would blank every identifier in the trace. The configuration guide says so; the code cannot tell
 * an intentional short pattern from a mistaken one.
 */
final class AdditionalRedactionPatterns {

  /** System property appending comma-separated field-name patterns to the built-in deny-list. */
  static final String PROPERTY = "narrativetrace.redaction.additionalPatterns";

  /** Environment variable read when {@link #PROPERTY} is not set. */
  static final String ENVIRONMENT_VARIABLE = "NARRATIVETRACE_REDACTION_ADDITIONALPATTERNS";

  private AdditionalRedactionPatterns() {}

  /**
   * The additions configured for this JVM, read at the moment a policy is constructed.
   *
   * @return the parsed patterns, empty when neither the property nor the variable is set
   */
  static Set<String> configured() {
    return configured(System::getProperty, System::getenv);
  }

  /**
   * The same lookup against supplied sources, so a test can exercise the fallback.
   *
   * <p><b>@llmNote</b> The environment cannot be set from inside a running JVM, so the only way to
   * test that the variable is read at all — and that the property outranks it — is to hand both
   * lookups in. Same shape as {@code ResourceIdentity.detectHostName}.
   *
   * @param properties where to read {@link #PROPERTY}
   * @param environment where to read {@link #ENVIRONMENT_VARIABLE} when the property is absent
   * @return the parsed patterns
   */
  static Set<String> configured(
      UnaryOperator<String> properties, UnaryOperator<String> environment) {
    var property = properties.apply(PROPERTY);
    return parse(property != null ? property : environment.apply(ENVIRONMENT_VARIABLE));
  }

  /**
   * Splits one configured value into patterns: comma-separated, each stripped, blanks dropped.
   *
   * @param raw the raw property or variable value; {@code null} and blank yield no patterns
   * @return the patterns, in no particular order
   */
  static Set<String> parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(raw.split(","))
        .map(String::strip)
        .filter(pattern -> !pattern.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }
}
