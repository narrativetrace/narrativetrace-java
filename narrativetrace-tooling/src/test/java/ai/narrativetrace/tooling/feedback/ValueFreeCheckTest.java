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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The gate over a whole report: which field broke which rule, and in what order. */
class ValueFreeCheckTest {

  @Test
  void namesTheFieldAndTheRuleThatRefusedIt() {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("happened", "it rendered as OrderService.placeOrder(id: \"C-1\")");

    assertThat(ValueFreeCheck.violations(fields))
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.field()).isEqualTo("happened");
              assertThat(v.rule()).isEqualTo(ValueFreeRule.RENDERED_CALL);
            });
  }

  @Test
  void aValueFreeReportHasNoViolationAtAll() {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("step", "narrativetrace-doctor, step 2");
    fields.put("install", "ai.narrativetrace:narrativetrace-core:0.2.4");
    fields.put("did", "ran ./gradlew narrativetraceDoctor after adding the plugin");
    fields.put("happened", "trap.redaction-proof failed and its fix did not help");
    fields.put("expected", "the check to pass once the test asserts the marker");
    fields.put(
        "trace",
        "scenario: Order is placed\n\n- OrderService.placeOrder(customerId, total)\n"
            + "  - Inventory.reserve(sku) → value");

    assertThat(ValueFreeCheck.violations(fields)).isEmpty();
  }

  @Test
  void reportsEveryRuleAFieldBreaks_inRuleOrder() {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("happened", "ada@example.com in /Users/ada/work");

    assertThat(ValueFreeCheck.violations(fields))
        .extracting(ValueFreeViolation::rule)
        .containsExactly(ValueFreeRule.EMAIL, ValueFreeRule.HOME_PATH);
  }

  @Test
  void refusesANullFieldMapAndANullFieldValue() {
    assertThatIllegalArgumentException().isThrownBy(() -> ValueFreeCheck.violations(null));
    Map<String, String> withNull = new LinkedHashMap<>();
    withNull.put("did", null);
    assertThatIllegalArgumentException().isThrownBy(() -> ValueFreeCheck.violations(withNull));
  }

  /**
   * The doctor's own check vocabulary NAMES the redaction marker: {@code trap.redaction-proof}'s
   * message and fix both quote {@code "[REDACTED]"}, and the check says so whether it passes or
   * fails. So `vf.marker` read against that one generated field refused every report from every
   * project that has NarrativeTrace installed at all — the verb could not draft anything.
   */
  @Test
  void theDoctorsOwnReportMayQuoteTheRedactionMarkerBecauseThatIsWhatItIsAbout() {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put(
        ValueFreeCheck.DOCTOR_REPORT_FIELD,
        "{\"findings\":[{\"id\":\"trap.redaction-proof\",\"status\":\"fail\","
            + "\"message\":\"No test file asserts the literal \\\"[REDACTED]\\\"\"}]}");

    assertThat(ValueFreeCheck.violations(fields)).isEmpty();
  }

  /** The exemption is ONE field and ONE rule: everywhere else the marker still proves a render. */
  @Test
  void theMarkerIsStillRefusedInEveryFieldAPersonWrote() {
    for (String field : List.of("did", "happened", "expected", "step", "install", "trace")) {
      Map<String, String> fields = new LinkedHashMap<>();
      fields.put(field, "it rendered as password: [REDACTED]");

      assertThat(ValueFreeCheck.violations(fields))
          .as(field)
          .extracting(ValueFreeViolation::rule)
          .contains(ValueFreeRule.MARKER);
    }
  }

  /**
   * And every OTHER rule still reads the doctor report, which is the whole point of exempting one.
   */
  @Test
  void everyOtherRuleStillReadsTheDoctorsReport() {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put(
        ValueFreeCheck.DOCTOR_REPORT_FIELD,
        "{\"findings\":[{\"message\":\"ada@example.com under /Users/ada/work with"
            + " ghp_abcd1234efgh\"}]}");

    assertThat(ValueFreeCheck.violations(fields))
        .extracting(ValueFreeViolation::rule)
        .contains(ValueFreeRule.EMAIL, ValueFreeRule.HOME_PATH, ValueFreeRule.VALUE_SHAPE);
  }

  /**
   * The exempt field is named by a string, so the gate and the report have to agree on it. A
   * renamed field would silently re-arm the rule and the verb would stop drafting anything again.
   */
  @Test
  void theExemptFieldIsOneTheReportActuallyCarries() {
    assertThat(Reports.complete().fields()).containsKey(ValueFreeCheck.DOCTOR_REPORT_FIELD);
  }

  /** `rulesRefusing` is text-scoped and has no field to exempt: the corpus reads it that way. */
  @Test
  void theTextScopedReadingOfTheRulesStillRefusesTheMarker() {
    assertThat(ValueFreeCheck.rulesRefusing("[REDACTED]")).contains(ValueFreeRule.MARKER);
  }
}
