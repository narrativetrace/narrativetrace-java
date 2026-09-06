/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.fuzz;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.Traceparent;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.nio.charset.StandardCharsets;

/**
 * Tier B target 1: coverage-guided fuzzing of the W3C wire reader.
 *
 * <p>INTENT: The jqwik property beside this one draws headers from an alphabet a human chose.
 * Jazzer draws them from the parser's own coverage, which is how it reaches the branch nobody
 * thought to describe — the field-count check against a version byte, the trailing-empty-field
 * rule, the interaction between them.
 *
 * <p><b>@llmNote</b> In {@code check} this is a regression test: Jazzer replays the committed seed
 * corpus under {@code TraceparentFuzzTestInputs/} and nothing else, in milliseconds. Only {@code
 * JAZZER_FUZZ=1} — which {@code ./gradlew fuzz} sets — turns it into a fuzzing run.
 *
 * <p><b>@edgeCase</b> The oracle is the same one the property holds, restated for arbitrary bytes:
 * parsing returns the declared result or {@code null}, never an exception, and anything accepted
 * survives {@code format()} unchanged.
 */
class TraceparentFuzzTest {

  @FuzzTest(maxDuration = FuzzBudget.PER_TARGET)
  void parsingAnyHeaderReturnsTheDeclaredResult(byte[] data) {
    var header = new String(data, StandardCharsets.UTF_8);

    var parsed = Traceparent.parse(header);

    if (parsed == null) {
      return;
    }
    assertThat(parsed.traceId().value()).matches("[0-9a-f]{32}");
    assertThat(parsed.parentSpanId().value()).matches("[0-9a-f]{16}");
    assertThat(parsed.traceFlags()).isBetween(0, 0xff);
    assertThat(Traceparent.parse(parsed.format())).isEqualTo(parsed);
  }
}
