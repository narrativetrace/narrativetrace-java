/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The listener's two property readers are deliberately asymmetric, and both halves matter: the
 * output opt-out is {@code narrativetrace.output=false} <em>case-insensitively</em> and nothing
 * else turns writing off, while every other switch is on only for {@code true} and off for
 * everything else. A near miss — {@code 0}, {@code off}, {@code no}, {@code yes} — is a value an
 * adopter reaches for from another tool's vocabulary, so what it does is part of the contract.
 */
class OutputFlagNearMissTest {

  @Test
  void anUppercaseFalseIsStillTheOptOut(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(
        outputDir, Map.of("narrativetrace.output", "FALSE"), testng -> {}, OrderFixture.class);

    assertThat(TestNgRuns.listing(outputDir)).isEmpty();
  }

  @Test
  void aNearMissForFalseLeavesOutputOn(@TempDir Path root) throws Exception {
    for (var value : new String[] {"0", "off", "no", "disabled", ""}) {
      var outputDir = root.resolve("out-" + value.length() + value);

      TestNgRuns.run(
          outputDir, Map.of("narrativetrace.output", value), testng -> {}, OrderFixture.class);

      assertThat(outputDir.resolve("traces/OrderFixture/customer_places_order.md"))
          .as("narrativetrace.output=%s is not the documented opt-out", value)
          .exists();
    }
  }

  @Test
  void anUppercaseTrueTurnsTheCanonicalJsonOn(@TempDir Path outputDir) throws Exception {
    TestNgRuns.run(
        outputDir,
        Map.of("narrativetrace.canonicalJson", "TRUE"),
        testng -> {},
        OrderFixture.class);

    assertThat(TestNgRuns.listing(outputDir))
        .anyMatch(file -> file.endsWith("customer_places_order.canonical.json"));
  }

  @Test
  void aNearMissForTrueLeavesTheOptInOff(@TempDir Path root) throws Exception {
    for (var value : new String[] {"yes", "1", "on", "True "}) {
      var outputDir = root.resolve("out-" + value.length() + value.strip());

      TestNgRuns.run(
          outputDir,
          Map.of("narrativetrace.canonicalJson", value),
          testng -> {},
          OrderFixture.class);

      assertThat(TestNgRuns.listing(outputDir))
          .as("narrativetrace.canonicalJson=%s is not \"true\"", value)
          .noneMatch(file -> file.endsWith(".canonical.json"));
    }
  }
}
