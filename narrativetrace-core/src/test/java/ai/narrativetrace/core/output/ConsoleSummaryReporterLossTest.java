/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.TraceLoss;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The suite footer's loss line: silent when nothing was lost, specific when something was. */
class ConsoleSummaryReporterLossTest {

  private final ConsoleSummaryReporter reporter = new ConsoleSummaryReporter();

  @Test
  void saysNothingWhenNothingWasLost() {
    var footer = reporter.formatSuiteFooter(5, "build/narrativetrace", scores(), TraceLoss.none());

    assertThat(footer).doesNotContain("Incomplete");
    assertThat(footer).contains("5 scenarios recorded");
  }

  @Test
  void reportsDroppedEventsAlone() {
    var footer =
        reporter.formatSuiteFooter(5, "build/narrativetrace", scores(), new TraceLoss(1204, 0, 0));

    assertThat(footer).contains("Incomplete: 1204 events dropped (buffer full)");
    assertThat(footer).doesNotContain("refused");
  }

  @Test
  void reportsRefusedScopesAlone() {
    var footer =
        reporter.formatSuiteFooter(5, "build/narrativetrace", scores(), new TraceLoss(0, 3, 4100));

    assertThat(footer)
        .contains("Incomplete: 3 async scopes refused (cap or uncollected), 4100 spans")
        .doesNotContain("buffer full");
  }

  @Test
  void reportsBothSourcesOnOneLine() {
    var footer =
        reporter.formatSuiteFooter(5, "build/narrativetrace", scores(), new TraceLoss(12, 1, 7));

    assertThat(footer)
        .contains("Incomplete: 12 events dropped (buffer full)")
        .contains("1 async scope refused (cap or uncollected), 7 spans")
        .doesNotContain("late span");
  }

  @Test
  void singularAndPluralReadCorrectly() {
    var footer =
        reporter.formatSuiteFooter(1, "build/narrativetrace", scores(), new TraceLoss(1, 1, 1));

    assertThat(footer).contains("1 event dropped").contains("1 async scope").contains("1 span");
  }

  @Test
  void reportsDiscardedSpansAlone() {
    var footer =
        reporter.formatSuiteFooter(
            5, "build/narrativetrace", scores(), new TraceLoss(0, 0, 0, 2000));

    assertThat(footer)
        .contains("Incomplete: 2000 late spans discarded (parent reset)")
        .doesNotContain("dropped (buffer full)")
        .doesNotContain("refused");
  }

  @Test
  void singularDiscardedSpanReadsCorrectly() {
    var footer =
        reporter.formatSuiteFooter(1, "build/narrativetrace", scores(), new TraceLoss(0, 0, 0, 1));

    assertThat(footer).contains("1 late span discarded").doesNotContain("late spans");
  }

  @Test
  void reportsTwoOfThreeSourcesSkippingTheMiddleOne() {
    var footer =
        reporter.formatSuiteFooter(
            5, "build/narrativetrace", scores(), new TraceLoss(12, 0, 0, 2000));

    assertThat(footer)
        .contains("Incomplete: 12 events dropped (buffer full)")
        .contains("2000 late spans discarded (parent reset)")
        .doesNotContain("refused");
  }

  @Test
  void reportsAllThreeSourcesOnOneLine() {
    var footer =
        reporter.formatSuiteFooter(
            5, "build/narrativetrace", scores(), new TraceLoss(1204, 3, 4100, 2000));

    assertThat(footer)
        .contains("Incomplete: 1204 events dropped (buffer full)")
        .contains("3 async scopes refused (cap or uncollected), 4100 spans")
        .contains("2000 late spans discarded (parent reset)");
    assertThat(footer.indexOf("events dropped")).isLessThan(footer.indexOf("refused"));
    assertThat(footer.indexOf("refused")).isLessThan(footer.indexOf("late spans discarded"));
  }

  @Test
  void theLossLineSitsAboveTheReportsPath() {
    var footer =
        reporter.formatSuiteFooter(5, "build/narrativetrace", scores(), new TraceLoss(2, 0, 0));

    assertThat(footer.indexOf("Incomplete:")).isLessThan(footer.indexOf("Reports:"));
  }

  @Test
  void theDiscardedSpansClauseAloneStillSitsAboveTheReportsPath() {
    var footer =
        reporter.formatSuiteFooter(5, "build/narrativetrace", scores(), new TraceLoss(0, 0, 0, 5));

    assertThat(footer.indexOf("Incomplete:")).isLessThan(footer.indexOf("Reports:"));
  }

  @Test
  void theExistingFooterKeepsWorkingWithoutALoss() {
    var footer = reporter.formatSuiteFooter(5, "build/narrativetrace", scores());

    assertThat(footer).contains("5 scenarios recorded").doesNotContain("Incomplete");
  }

  private static List<Double> scores() {
    return List.of(0.9, 0.8);
  }
}
