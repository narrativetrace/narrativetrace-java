/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClarityJsonExporterTest {

  private final ClarityJsonExporter exporter = new ClarityJsonExporter();

  @Test
  void controlCharactersInScenarioNameAreEscaped() {
    var result = new ClarityResult(0.85, 0.90, 0.95, 0.80, 1.00, 0.70, List.of());
    var hostileName = "Scenario" + (char) 1 + " \b\f \"quoted\"";

    String json = exporter.export(Map.of(hostileName, result));

    assertThat(json)
        .contains("\"name\":\"Scenario\\u0001 \\b\\f \\\"quoted\\\"\"")
        .doesNotContain("\b")
        .doesNotContain("\f");
  }

  @Test
  void emptyMapProducesEmptyScenarios() {
    String json = exporter.export(Map.of());

    assertThat(json).isEqualTo("{\"version\":\"1.2\",\"scenarios\":[],\"suiteIssues\":[]}");
  }

  @Test
  void singleScenarioWithScoresNoIssues() {
    var result = new ClarityResult(0.85, 0.90, 0.95, 0.80, 1.00, 0.70, List.of());
    var results = Map.of("Customer places order", result);

    String json = exporter.export(results);

    assertThat(json)
        .isEqualTo(
            "{\"version\":\"1.2\",\"scenarios\":["
                + "{\"name\":\"Customer places order\""
                + ",\"overallScore\":0.85"
                + ",\"methodNameScore\":0.90"
                + ",\"classNameScore\":0.95"
                + ",\"parameterNameScore\":0.80"
                + ",\"structuralScore\":1.00"
                + ",\"cohesionScore\":0.70"
                + ",\"issues\":[]"
                + ",\"elements\":[]"
                + "}"
                + "],\"suiteIssues\":[]}");
  }

  @Test
  void scenarioWithIssuesSerialized() {
    var issue =
        new ClarityIssue(
            "param-name",
            "data",
            "Use a domain-specific name",
            ClarityIssue.Severity.MEDIUM,
            2,
            4.0);
    var result = new ClarityResult(0.65, 0.70, 0.80, 0.50, 1.00, 0.60, List.of(issue));
    var results = Map.of("Checkout flow", result);

    String json = exporter.export(results);

    assertThat(json)
        .contains("\"issues\":[{")
        .contains("\"category\":\"param-name\"")
        .contains("\"element\":\"data\"")
        .contains("\"suggestion\":\"Use a domain-specific name\"")
        .contains("\"severity\":\"MEDIUM\"")
        .contains("\"occurrences\":2")
        .contains("\"impactScore\":4.00");
  }

  @Test
  void multipleScenariosPreserveOrder() {
    var results = new LinkedHashMap<String, ClarityResult>();
    results.put("Alpha", new ClarityResult(0.90, 0.90, 0.90, 0.90, 0.90, 0.90, List.of()));
    results.put("Beta", new ClarityResult(0.80, 0.80, 0.80, 0.80, 0.80, 0.80, List.of()));

    String json = exporter.export(results);

    int alphaIndex = json.indexOf("\"name\":\"Alpha\"");
    int betaIndex = json.indexOf("\"name\":\"Beta\"");
    assertThat(alphaIndex).isLessThan(betaIndex);
    assertThat(json).contains("},{");
  }

  @Test
  void scoresUseDecimalPointRegardlessOfLocale() {
    var original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.GERMANY); // German locale uses comma: 0,85
      var result = new ClarityResult(0.85, 0.90, 0.95, 0.80, 1.00, 0.70, List.of());
      String json = new ClarityJsonExporter().export(Map.of("test", result));

      assertThat(json).contains("\"overallScore\":0.85");
      assertThat(json).contains("\"methodNameScore\":0.90");
      assertThat(json).doesNotContain(",85").doesNotContain(",90");
    } finally {
      Locale.setDefault(original);
    }
  }

  @Test
  void specialCharactersEscaped() {
    var issue =
        new ClarityIssue(
            "method-name", "do\"stuff", "Use a \\proper\\ name", ClarityIssue.Severity.LOW, 1, 1.0);
    var result = new ClarityResult(0.50, 0.50, 0.50, 0.50, 0.50, 0.50, List.of(issue));
    var results = Map.of("Scenario with \"quotes\"", result);

    String json = exporter.export(results);

    assertThat(json)
        .contains("\"name\":\"Scenario with \\\"quotes\\\"\"")
        .contains("\"element\":\"do\\\"stuff\"")
        .contains("\"suggestion\":\"Use a \\\\proper\\\\ name\"");
  }

  @Test
  void newlinesInStringsAreEscaped() {
    var issue =
        new ClarityIssue(
            "method-name",
            "Service.process",
            "Use a better name\nwith a concrete example",
            ClarityIssue.Severity.MEDIUM,
            1,
            2.0);
    var result = new ClarityResult(0.40, 0.30, 0.50, 0.20, 1.00, 0.70, List.of(issue));

    var json = exporter.export(Map.of("Scenario\nOne", result));

    assertThat(json).contains("\"name\":\"Scenario\\nOne\"");
    assertThat(json).contains("\"suggestion\":\"Use a better name\\nwith a concrete example\"");
    assertThat(json).doesNotContain("Scenario\nOne");
    assertThat(json).doesNotContain("better name\nwith");
  }

  @Test
  void suiteIssuesAreSerializedAsTopLevelArray() {
    var suiteIssue =
        new ClarityIssue(
                "non-canonical-term",
                "billing.OverdraftService.openAccountWithOverdraft",
                "use canonical term 'overdraft account'")
            .withOccurrences(2);

    String json = exporter.export(List.of(), List.of(suiteIssue));

    assertThat(json)
        .isEqualTo(
            "{\"version\":\"1.2\",\"scenarios\":[],\"suiteIssues\":["
                + "{\"category\":\"non-canonical-term\""
                + ",\"element\":\"billing.OverdraftService.openAccountWithOverdraft\""
                + ",\"suggestion\":\"use canonical term 'overdraft account'\""
                + ",\"severity\":\"MEDIUM\""
                + ",\"occurrences\":2"
                + ",\"impactScore\":4.00"
                + "}"
                + "]}");
  }

  @Test
  void suiteIssuesCoexistWithScenarioIssuesWithoutMixing() {
    var scenarioIssue =
        new ClarityIssue("param-name", "data", "fix param", ClarityIssue.Severity.MEDIUM, 1, 2.0);
    var result = new ClarityResult(0.65, 0.70, 0.80, 0.50, 1.00, 0.60, List.of(scenarioIssue));
    var suiteIssues =
        List.of(
            new ClarityIssue("non-canonical-term", "shop.Cart.addItm", "use 'item'"),
            new ClarityIssue("non-canonical-term", "shop.Cart.chkOut", "use 'check out'"));

    String json =
        exporter.export(List.of(Map.entry("Checkout flow", (ClarityResult) result)), suiteIssues);

    assertThat(json)
        .contains("\"issues\":[{\"category\":\"param-name\"")
        .contains("}],\"suiteIssues\":[{\"category\":\"non-canonical-term\"")
        .contains("},{\"category\":\"non-canonical-term\",\"element\":\"shop.Cart.chkOut\"");
    assertThat(json.indexOf("shop.Cart.addItm")).isGreaterThan(json.indexOf("\"suiteIssues\""));
  }

  @Test
  void multipleIssuesAreCommaSeparated() {
    var issue1 =
        new ClarityIssue(
            "method-name", "process", "fix method", ClarityIssue.Severity.HIGH, 1, 3.0);
    var issue2 =
        new ClarityIssue("param-name", "data", "fix param", ClarityIssue.Severity.MEDIUM, 1, 2.0);
    var result = new ClarityResult(0.40, 0.30, 0.50, 0.20, 1.00, 0.70, List.of(issue1, issue2));
    var results = Map.of("Test", result);

    String json = exporter.export(results);

    // Issues should be comma-separated: },{
    assertThat(json).contains("},{\"category\":\"param-name\"");
  }

  @Test
  void scenarioElementsSerializedAsSchema12Array() {
    var note =
        new ElementNote("method", "OrderService.processOrder", 0.62, "Generic verb 'process'");
    var result = new ClarityResult(0.85, 0.90, 0.95, 0.80, 1.00, 0.70, List.of(), List.of(note));

    String json = exporter.export(Map.of("Checkout", result));

    assertThat(json).contains("\"version\":\"1.2\"");
    assertThat(json)
        .contains(
            "\"elements\":[{"
                + "\"kind\":\"method\""
                + ",\"element\":\"OrderService.processOrder\""
                + ",\"score\":0.62"
                + ",\"note\":\"Generic verb 'process'\"}]");
  }

  @Test
  void multipleElementsAreCommaSeparated() {
    var methodNote = new ElementNote("method", "Svc.processData", 0.30, "Generic verb 'process'");
    var paramNote = new ElementNote("parameter", "data", 0.10, "Vague name 'data'");
    var result =
        new ClarityResult(
            0.40, 0.30, 0.50, 0.20, 1.00, 0.70, List.of(), List.of(methodNote, paramNote));

    String json = exporter.export(Map.of("Test", result));

    assertThat(json).contains("\"note\":\"Generic verb 'process'\"},{\"kind\":\"parameter\"");
    assertThat(json.indexOf("Svc.processData")).isLessThan(json.indexOf("\"element\":\"data\""));
  }

  @Test
  void elementNoteSpecialCharactersAreEscaped() {
    var note = new ElementNote("method", "Svc.do\"it", 0.10, "note with \"quote\" and \\slash");
    var result = new ClarityResult(0.50, 0.50, 0.50, 0.50, 0.50, 0.50, List.of(), List.of(note));

    String json = exporter.export(Map.of("Scenario", result));

    assertThat(json)
        .contains("\"element\":\"Svc.do\\\"it\"")
        .contains("\"note\":\"note with \\\"quote\\\" and \\\\slash\"");
  }
}
