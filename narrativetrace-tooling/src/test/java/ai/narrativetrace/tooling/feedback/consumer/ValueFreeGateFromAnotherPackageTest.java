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
package ai.narrativetrace.tooling.feedback.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.tooling.feedback.ValueFreeCheck;
import ai.narrativetrace.tooling.feedback.ValueFreeRule;
import ai.narrativetrace.tooling.feedback.ValueFreeViolation;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The gate as a CALLER sees it — the Gradle task, the CLI verb and every port's equivalent live
 * outside this package, so everything they need has to be reachable from outside it.
 *
 * <p>Same-package tests would pass against a gate whose rule ids, reasons or violation text were
 * package-private: those three strings are the whole product of a refusal, and a refusal nobody can
 * print is a gate that only says no.
 */
class ValueFreeGateFromAnotherPackageTest {

  @Test
  void everyRuleExposesItsIdAndItsReasonToACaller() {
    assertThat(ValueFreeRule.all())
        .hasSize(10)
        .allSatisfy(
            rule -> {
              assertThat(rule.id()).startsWith("vf.");
              assertThat(rule.reason()).isNotBlank();
            });
  }

  @Test
  void aRefusalPrintsTheFieldTheRuleIdAndWhatToDo() {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("happened", "Authorization: Bearer abc");

    ValueFreeViolation violation = ValueFreeCheck.violations(fields).get(0);

    assertThat(violation.describe())
        .startsWith("happened: vf.named-secret — ")
        .contains("credential");
  }

  @Test
  void theRuleIdsAreTheOnesEveryRuntimeAgreedOn() {
    assertThat(ValueFreeRule.all())
        .extracting(ValueFreeRule::id)
        .containsExactly(
            "vf.rendered-call",
            "vf.rendered-outcome",
            "vf.duration",
            "vf.marker",
            "vf.named-secret",
            "vf.value-shape",
            "vf.entropy",
            "vf.email",
            "vf.home-path",
            "vf.control");
  }
}
