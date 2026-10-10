/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.tooling.doctor.DoctorChecks;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The skills tell an agent how many findings a complete doctor report names; the doctor's registry
 * decides it. A new check — a new framework row, say — must move the published verify with it, or
 * an agent following the skill reads a correct report as incomplete.
 */
class DoctorCountDriftTest {

  private static final List<String> NUMBER_WORDS =
      List.of(
          "zero",
          "one",
          "two",
          "three",
          "four",
          "five",
          "six",
          "seven",
          "eight",
          "nine",
          "ten",
          "eleven",
          "twelve",
          "thirteen",
          "fourteen",
          "fifteen",
          "sixteen",
          "seventeen",
          "eighteen",
          "nineteen",
          "twenty",
          "twenty-one",
          "twenty-two",
          "twenty-three",
          "twenty-four",
          "twenty-five");

  @Test
  void theVerifyNamesTheNumberOfChecksTheDoctorRuns() {
    int checks = DoctorChecks.ALL.size();
    assertThat(checks).as("extend NUMBER_WORDS").isLessThan(NUMBER_WORDS.size());
    assertThat(DoctorCommands.VERIFY_EVERY_FINDING)
        .contains("naming all " + NUMBER_WORDS.get(checks) + " findings");
  }
}
