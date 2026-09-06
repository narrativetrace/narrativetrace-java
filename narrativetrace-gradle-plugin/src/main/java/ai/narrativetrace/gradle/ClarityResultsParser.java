/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import groovy.json.JsonSlurper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parser for the JSON emitted by {@code ClarityJsonExporter}.
 *
 * <p>INTENT: Keep Gradle-side verification decoupled from the clarity module's internal types.
 *
 * <p>Schema-tolerant by construction: {@code JsonSlurper} ignores unknown object keys, so additive
 * fields introduced by newer exporter schemas (the top-level {@code suiteIssues} array of 1.1, the
 * per-scenario {@code elements} array of 1.2) parse without any change here. Only the {@code
 * version} and {@code scenarios} fields are required.
 */
public final class ClarityResultsParser {

  private ClarityResultsParser() {}

  @SuppressWarnings("unchecked")
  public static List<ClarityScenarioResult> parse(String json) {
    var parsed = parseRoot(json);
    var scenarios = (List<Map<String, Object>>) parsed.get("scenarios");
    var results = new ArrayList<ClarityScenarioResult>();
    for (var scenario : scenarios) {
      results.add(parseScenario(scenario));
    }
    return results;
  }

  /**
   * Parses the top-level {@code suiteIssues} array introduced with schema 1.1.
   *
   * <p>Schema 1.0 files carry no {@code suiteIssues} field; they parse to an empty list so old
   * artifacts remain readable.
   */
  @SuppressWarnings("unchecked")
  public static List<ClarityScenarioResult.Issue> parseSuiteIssues(String json) {
    var parsed = parseRoot(json);
    var suiteIssues = (List<Map<String, Object>>) parsed.getOrDefault("suiteIssues", List.of());
    var issues = new ArrayList<ClarityScenarioResult.Issue>();
    for (var issue : suiteIssues) {
      issues.add(parseIssue(issue));
    }
    return List.copyOf(issues);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> parseRoot(String json) {
    Map<String, Object> parsed;
    try {
      parsed = (Map<String, Object>) new JsonSlurper().parseText(json);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to parse clarity results: " + e.getMessage(), e);
    }
    if (!parsed.containsKey("version")) {
      throw new IllegalArgumentException(
          "Failed to parse clarity results: missing 'version' field");
    }
    if (!parsed.containsKey("scenarios")) {
      throw new IllegalArgumentException(
          "Failed to parse clarity results: missing 'scenarios' field");
    }
    return parsed;
  }

  @SuppressWarnings("unchecked")
  private static ClarityScenarioResult parseScenario(Map<String, Object> scenario) {
    var issuesList = (List<Map<String, Object>>) scenario.get("issues");
    var issues = new ArrayList<ClarityScenarioResult.Issue>();
    for (var issue : issuesList) {
      issues.add(parseIssue(issue));
    }
    return new ClarityScenarioResult(
        (String) scenario.get("name"),
        toDouble(scenario.get("overallScore")),
        toDouble(scenario.get("methodNameScore")),
        toDouble(scenario.get("classNameScore")),
        toDouble(scenario.get("parameterNameScore")),
        toDouble(scenario.get("structuralScore")),
        toDouble(scenario.get("cohesionScore")),
        List.copyOf(issues));
  }

  private static ClarityScenarioResult.Issue parseIssue(Map<String, Object> issue) {
    return new ClarityScenarioResult.Issue(
        (String) issue.get("category"),
        (String) issue.get("element"),
        (String) issue.get("suggestion"),
        (String) issue.get("severity"),
        ((Number) issue.get("occurrences")).intValue(),
        toDouble(issue.get("impactScore")));
  }

  private static double toDouble(Object value) {
    return ((Number) value).doubleValue();
  }
}
