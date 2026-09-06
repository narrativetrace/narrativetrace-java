/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConsoleSummaryReporterTest {

  @Test
  void formatsPassingTestAsOneLine() {
    var reporter = new ConsoleSummaryReporter();

    var line = reporter.formatTestResult("customerPlacesOrderSuccessfully", 412);

    assertThat(line).isEqualTo("    ✓ customerPlacesOrderSuccessfully (412ms)");
  }

  @Test
  void formatsPassingTestWithClarityScore() {
    var reporter = new ConsoleSummaryReporter();

    var line = reporter.formatTestResult("customerPlacesOrderSuccessfully", 412, 0.82);

    assertThat(line).isEqualTo("    ✓ customerPlacesOrderSuccessfully (412ms, clarity: 0.82)");
  }

  @Test
  void formatsSuiteFooterWithScenarioCountAndPath() {
    var reporter = new ConsoleSummaryReporter();

    var footer = reporter.formatSuiteFooter(347, "target/narrativetrace/");

    assertThat(footer).contains("347 scenarios recorded");
    assertThat(footer).contains("target/narrativetrace/");
  }

  @Test
  void formatsSuiteFooterWithClarityBreakdown() {
    var reporter = new ConsoleSummaryReporter();

    var footer =
        reporter.formatSuiteFooter(
            100,
            "target/narrativetrace/",
            List.of(0.85, 0.92, 0.78, 0.65, 0.45, 0.30, 0.88, 0.71, 0.55, 0.40));

    assertThat(footer).contains("100 scenarios recorded");
    assertThat(footer).contains("Clarity:");
    assertThat(footer).contains("high");
    assertThat(footer).contains("moderate");
    assertThat(footer).contains("low");
  }

  @Test
  void clarityBreakdownCategorizesScoresCorrectly() {
    var reporter = new ConsoleSummaryReporter();
    // 3 high (>=0.7), 1 moderate (>=0.4), 1 low (<0.4)
    var footer =
        reporter.formatSuiteFooter(
            5, "target/narrativetrace/", List.of(0.90, 0.80, 0.70, 0.50, 0.30));

    assertThat(footer).contains("60% high");
    assertThat(footer).contains("20% moderate");
    assertThat(footer).contains("20% low");
  }

  @Test
  void formatsFailingTestWithErrorDetail() {
    var reporter = new ConsoleSummaryReporter();

    var line =
        reporter.formatTestFailure(
            "chargeWithNetworkTimeout",
            1203,
            "ConnectionTimeoutException",
            "PaymentGateway.java:87",
            "target/narrativetrace/traces/PaymentServiceTest/charge_with_network_timeout.md");

    assertThat(line).contains("✗ chargeWithNetworkTimeout (1203ms)");
    assertThat(line).contains("ConnectionTimeoutException");
    assertThat(line).contains("PaymentGateway.java:87");
    assertThat(line)
        .contains("target/narrativetrace/traces/PaymentServiceTest/charge_with_network_timeout.md");
  }

  @Test
  void formatsSuiteHeader() {
    var reporter = new ConsoleSummaryReporter();

    var header = reporter.formatSuiteHeader();

    assertThat(header).isEqualTo("NarrativeTrace — Recording test narratives\n");
  }

  @Test
  void deltaLineCountsUnchangedScenarios() {
    var reporter = new ConsoleSummaryReporter();

    var line =
        reporter.formatDeltaLine(
            List.of(unchanged("Order ships"), unchanged("Order cancels"), unchanged("Order pays")));

    assertThat(line).isEqualTo("3 scenarios unchanged");
  }

  @Test
  void deltaLineNamesTheChangedScenarioWithItsSummary() {
    var reporter = new ConsoleSummaryReporter();

    var line =
        reporter.formatDeltaLine(
            List.of(
                unchanged("Order ships"),
                unchanged("Order cancels"),
                new ScenarioDelta(
                    "Weekend trip settles",
                    ScenarioDelta.Kind.CHANGED,
                    "+4 calls CurrencyConverter.toBaseCurrency",
                    "diff")));

    assertThat(line)
        .isEqualTo(
            "2 scenarios unchanged · 1 changed: \"Weekend trip settles\""
                + " (+4 calls CurrencyConverter.toBaseCurrency)");
  }

  @Test
  void deltaLineCountsNewScenariosBetweenUnchangedAndChanged() {
    var reporter = new ConsoleSummaryReporter();

    var line =
        reporter.formatDeltaLine(
            List.of(
                unchanged("Order ships"),
                new ScenarioDelta("Order refunds", ScenarioDelta.Kind.NEW, "", ""),
                new ScenarioDelta(
                    "Weekend trip settles", ScenarioDelta.Kind.CHANGED, "structure changed", "d")));

    assertThat(line)
        .isEqualTo(
            "1 scenario unchanged · 1 new · 1 changed:"
                + " \"Weekend trip settles\" (structure changed)");
  }

  @Test
  void deltaLineTruncatesLongScenarioNames() {
    var reporter = new ConsoleSummaryReporter();

    var line =
        reporter.formatDeltaLine(
            List.of(
                new ScenarioDelta(
                    "Weekend trip settles with three transfers across two currencies",
                    ScenarioDelta.Kind.CHANGED,
                    "+4 calls CurrencyConverter.toBaseCurrency",
                    "d")));

    assertThat(line)
        .isEqualTo(
            "1 scenario changed: \"Weekend trip settles with three…\""
                + " (+4 calls CurrencyConverter.toBaseCurrency)");
  }

  @Test
  void deltaLineIsEmptyWhenNothingWasCompared() {
    var reporter = new ConsoleSummaryReporter();

    assertThat(reporter.formatDeltaLine(List.of())).isEmpty();
  }

  private static ScenarioDelta unchanged(String scenario) {
    return new ScenarioDelta(scenario, ScenarioDelta.Kind.UNCHANGED, "", "");
  }
}
