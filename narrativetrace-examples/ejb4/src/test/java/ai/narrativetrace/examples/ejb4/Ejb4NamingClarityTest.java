/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ejb4;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.clarity.ClarityIssue;
import ai.narrativetrace.clarity.ClarityReportRenderer;
import ai.narrativetrace.clarity.ClarityResult;
import ai.narrativetrace.clarity.ClarityScanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The clarity tie-in (plan Phase 2 milestone 5): the deliberate EJB-era naming of this example
 * ({@code FraudChkMgr}, {@code chkClaim}, {@code CoverageCalcEJB}, {@code calcPayout}) is exactly
 * what the clarity analyzer exists to call out — this test runs {@link ClarityScanner} over the
 * example's compiled classes and pins the "rename these" report.
 *
 * <p>Decision (2026-08-18, resolving the plan's open question): the report runs neither
 * in-container nor over exported canonical JSON — names are a static property of the code the trace
 * exercises, so the classpath scanner produces the same report without a container, and the WAR
 * keeps zero NarrativeTrace dependencies (clarity is test-scope only, jars never enter
 * WEB-INF/lib).
 */
class Ejb4NamingClarityTest {

  private static final Path CLASSES_DIR = Path.of("build/classes/java/main");
  private static final Path REPORT_FILE = Path.of("build/narrativetrace/ejb4-clarity-report.md");

  @Test
  void flagsTheDeliberateEjbEraNamesForRenaming() throws IOException {
    var results = new ClarityScanner().scan(CLASSES_DIR);

    assertThat(results).containsKeys("FraudChkMgr", "CoverageCalcEJB", "ClaimsProcessorBean");
    assertThat(issuesOf(results, "FraudChkMgr"))
        .anyMatch(
            i ->
                i.category().equals("abbreviation")
                    && i.element().equals("FraudChkMgr")
                    && i.suggestion().contains("chk → check")
                    && i.suggestion().contains("mgr → manager"));
    assertThat(issuesOf(results, "FraudChkMgr"))
        .anyMatch(
            i -> i.category().equals("abbreviation") && i.element().equals("FraudChkMgr.chkClaim"));
    assertThat(issuesOf(results, "CoverageCalcEJB"))
        .anyMatch(
            i ->
                i.category().equals("abbreviation") && i.suggestion().contains("calc → calculate"));
  }

  @Test
  void writesTheRenameReportNextToTheOtherTraceArtifacts() throws IOException {
    var results = new ClarityScanner().scan(CLASSES_DIR);
    var renderer = new ClarityReportRenderer();

    // The suite report details only scenarios scoring below its threshold; the legacy names sit
    // just above it, so append the always-detailed per-class report for every class with issues.
    var report = new StringBuilder(renderer.renderSuiteReport(results));
    results.entrySet().stream()
        .filter(e -> !e.getValue().issues().isEmpty())
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> report.append("\n\n").append(renderer.render(e.getKey(), e.getValue())));
    Files.createDirectories(REPORT_FILE.getParent());
    Files.writeString(REPORT_FILE, report.toString());

    var written = Files.readString(REPORT_FILE);
    assertThat(written).contains("FraudChkMgr").contains("chk → check", "mgr → manager");
  }

  private static List<ClarityIssue> issuesOf(Map<String, ClarityResult> results, String className) {
    return results.get(className).issues();
  }
}
