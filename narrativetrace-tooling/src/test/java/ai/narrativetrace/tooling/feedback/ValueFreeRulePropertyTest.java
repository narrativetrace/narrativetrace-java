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
package ai.narrativetrace.tooling.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * The two properties the gate exists for, each pinning a CLASS of input rather than one row of the
 * corpus: a rendered artifact never gets through, and a report built out of ordinary identifiers
 * and prose always does.
 *
 * <p><b>@llmNote</b> The second property's generators are deliberately realistic rather than
 * arbitrary. An arbitrary string eventually produces 32 hex characters or a {@code key: value}
 * whose key happens to contain {@code pan}, and a property that has to exclude its own refusals
 * stops being a property. What is claimed here is what the product promises — a report a person
 * writes about their own code is filable — not that no string anywhere is refused.
 */
class ValueFreeRulePropertyTest {

  // --- the properties ---------------------------------------------------------------------------

  /** Every rendered call line, whatever it names and whatever value it carries, is refused. */
  @Property(tries = 500)
  void aRenderedCallIsNeverFilable(
      @ForAll("identifier") String type,
      @ForAll("identifier") String method,
      @ForAll("identifier") String parameter,
      @ForAll("renderedValue") String value) {
    String line = type + "." + method + "(" + parameter + ": " + value + ")";

    assertThat(ValueFreeCheck.rulesRefusing(line))
        .as(line + " is a rendered call and must never pass the gate")
        .contains(ValueFreeRule.RENDERED_CALL);
  }

  /** Every rendered duration suffix is refused, at every unit and precision. */
  @Property(tries = 500)
  void aRenderedDurationIsNeverFilable(
      @ForAll("identifier") String method,
      @ForAll java.math.BigInteger elapsed,
      @ForAll("timeUnit") String unit) {
    String line = method + "() — " + elapsed.abs() + unit;

    assertThat(ValueFreeCheck.rulesRefusing(line))
        .as(line + " carries an elapsed time and must never pass the gate")
        .contains(ValueFreeRule.DURATION);
  }

  /** A structural trace of ordinary identifiers passes every rule, at every depth. */
  @Property(tries = 500)
  void aStructuralTraceOfOrdinaryIdentifiersIsAlwaysFilable(
      @ForAll("identifier") String type,
      @ForAll("identifier") String method,
      @ForAll("identifier") String parameter,
      @ForAll("outcome") String outcome) {
    String trace =
        "scenario: "
            + method
            + "\n\n- "
            + type
            + "."
            + method
            + "("
            + parameter
            + ")"
            + outcome
            + "\n";

    assertThat(ValueFreeCheck.rulesRefusing(trace))
        .as(trace + " is a structural trace and must always pass the gate")
        .isEmpty();
  }

  /** A report written in ordinary prose about ordinary identifiers passes every rule. */
  @Property(tries = 500)
  void aReportOfOrdinaryProseIsAlwaysFilable(
      @ForAll("prose") String did,
      @ForAll("prose") String happened,
      @ForAll("prose") String expected,
      @ForAll("identifier") String step) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("step", step);
    fields.put("did", did);
    fields.put("happened", happened);
    fields.put("expected", expected);

    assertThat(ValueFreeCheck.violations(fields)).isEmpty();
  }

  // --- generators -------------------------------------------------------------------------------

  /** A Java-shaped identifier built from a pool of words, the way real code names things. */
  @Provide
  Arbitrary<String> identifier() {
    return Arbitraries.of(
            "order",
            "customer",
            "inventory",
            "settle",
            "reserve",
            "placeOrder",
            "OrderService",
            "TripLedger",
            "recordExpense",
            "sku",
            "total",
            "tripName")
        .list()
        .ofMinSize(1)
        .ofMaxSize(3)
        .map(words -> String.join("", words));
  }

  /** What a rendered artifact puts after the colon: a quoted string, a number, a boolean. */
  @Provide
  Arbitrary<String> renderedValue() {
    return Arbitraries.oneOf(
        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(12).map(s -> '"' + s + '"'),
        Arbitraries.integers().between(0, 99_999).map(String::valueOf),
        Arbitraries.of("true", "false"));
  }

  @Provide
  Arbitrary<String> timeUnit() {
    return Arbitraries.of("ns", "µs", "ms", "s");
  }

  /** The three structural outcome markers, and the void case that has none. */
  @Provide
  Arbitrary<String> outcome() {
    return Arbitraries.of("", " → value", " !! IllegalStateException", " ?? incomplete");
  }

  /** A sentence of ordinary words — what a person writes in the three free-text fields. */
  @Provide
  Arbitrary<String> prose() {
    return Arbitraries.of(
            "the doctor reported a finding",
            "nothing was written under build",
            "the fix did not work",
            "I ran the task twice",
            "the extension never seemed to run",
            "parameter names came out as arg0",
            "I expected the check to pass")
        .list()
        .ofMinSize(1)
        .ofMaxSize(4)
        .map(sentences -> String.join(", and ", sentences));
  }
}
