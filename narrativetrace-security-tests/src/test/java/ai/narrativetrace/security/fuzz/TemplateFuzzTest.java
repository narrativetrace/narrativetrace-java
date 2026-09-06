/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.fuzz;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.template.TemplateParser;
import ai.narrativetrace.security.corpus.HostileGraphs;
import ai.narrativetrace.security.oracle.Oracles;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.nio.charset.StandardCharsets;

/**
 * Tier B target 4: coverage-guided fuzzing of template parsing and resolution.
 *
 * <p>INTENT: The placeholder grammar is small enough that a fuzzer can learn it from coverage —
 * braces, dots, and the identifiers of whichever fixture graph it resolves against. Every fixture
 * is tried for each input, so a path that reaches a redacted member through any of them is
 * explored.
 *
 * <p><b>@edgeCase</b> The oracle is redaction, not resolution. A template that resolves to nothing
 * is fine; a template that resolves to the sentinel is the leak class the owner ruled on, and it
 * has now been found twice — once for {@code {card.cvv}} and once for {@code {card}}.
 */
class TemplateFuzzTest {

  private static final String[] FIXTURES = {
    "card",
    "user",
    "order",
    "deep",
    "unicode",
    "wide",
    "chain",
    "password-scalar",
    "jwt-scalar",
    "newline-scalar"
  };

  @FuzzTest(maxDuration = FuzzBudget.PER_TARGET)
  void resolvingNeverLeaksARedactedValue(byte[] data) {
    var template = new String(data, StandardCharsets.UTF_8);
    var sentinel = Oracles.freshSentinel();

    for (var fixture : FIXTURES) {
      var resolved =
          TemplateParser.resolve(template, HostileGraphs.templateValues(fixture, sentinel));

      assertThat(resolved)
          .as("fixture %s leaked through %s", fixture, template)
          .doesNotContain(sentinel);
    }
  }
}
