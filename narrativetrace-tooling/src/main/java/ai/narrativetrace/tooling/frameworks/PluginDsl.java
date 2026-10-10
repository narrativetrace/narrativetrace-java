/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.tooling.frameworks;

import java.util.regex.Pattern;

/**
 * How a project that applies the {@code ai.narrativetrace} Gradle plugin asks for a row's module:
 * one property of the {@code narrativeTrace} extension, either at its top level ({@code mode},
 * {@code testFramework}) or inside its {@code modules} block.
 *
 * <p>Structured rather than a free-text line so the same value renders the line a reader copies
 * ({@link #line()}) AND recognises the line in a build file ({@link #pattern()}) — Kotlin DSL
 * {@code springWeb.set(true)} and Groovy DSL {@code springWeb = true} alike — and so the plugin's
 * own test can prove that the property really adds the row's module.
 *
 * @param property the extension property, e.g. {@code springWeb} or {@code mode}
 * @param value the value exactly as Kotlin DSL writes it, e.g. {@code true} or {@code "spring"}
 * @param inModulesBlock whether the property lives in {@code narrativeTrace { modules { … } } }
 */
public record PluginDsl(String property, String value, boolean inModulesBlock) {

  public PluginDsl {
    if (property == null || !property.matches("[a-zA-Z][a-zA-Z0-9]*")) {
      throw new IllegalArgumentException("a plugin property is one identifier, got " + property);
    }
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("a plugin property needs a value");
    }
  }

  /**
   * The Kotlin DSL line a reader adds to {@code build.gradle.kts}, in the plugin's default scope.
   */
  public String line() {
    return line(false);
  }

  /**
   * The Kotlin DSL line, optionally asking for the plugin's {@code production} scope first.
   *
   * <p><b>@llmNote</b> The plugin's default scope is {@code test}: every library it adds lands on
   * {@code testImplementation}. A module whose wiring is compiled under {@code src/main} needs
   * {@code scope.set("production")} beside the setting, or the fix the doctor prints does not
   * compile.
   */
  public String line(boolean productionScope) {
    String setting = property + ".set(" + value + ")";
    String body = inModulesBlock ? "modules { " + setting + " }" : setting;
    return "narrativeTrace { "
        + (productionScope ? "scope.set(\"production\"); " : "")
        + body
        + " }";
  }

  /** Recognises the setting in Kotlin ({@code .set(…)}) or Groovy ({@code = …}) DSL. */
  public Pattern pattern() {
    String unquoted = value.replace("\"", "");
    String quotedEither = value.startsWith("\"") ? "[\"']" : "";
    return Pattern.compile(
        "\\b"
            + Pattern.quote(property)
            + "\\s*(?:\\.set\\(\\s*|=\\s*)"
            + quotedEither
            + Pattern.quote(unquoted)
            + quotedEither
            + "(?![\\w-])");
  }
}
