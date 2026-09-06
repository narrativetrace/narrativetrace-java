/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ClarityResultsParserTest {

  @Test
  void parseEmptyScenarios() {
    var json = "{\"version\":\"1.0\",\"scenarios\":[]}";

    var results = ClarityResultsParser.parse(json);

    assertThat(results).isEmpty();
  }

  @Test
  void parseSingleScenarioWithScores() {
    var json =
        "{\"version\":\"1.0\",\"scenarios\":["
            + "{\"name\":\"Customer places order\""
            + ",\"overallScore\":0.85"
            + ",\"methodNameScore\":0.90"
            + ",\"classNameScore\":0.95"
            + ",\"parameterNameScore\":0.80"
            + ",\"structuralScore\":1.00"
            + ",\"cohesionScore\":0.70"
            + ",\"issues\":[]}"
            + "]}";

    var results = ClarityResultsParser.parse(json);

    assertThat(results).hasSize(1);
    var scenario = results.get(0);
    assertThat(scenario.name()).isEqualTo("Customer places order");
    assertThat(scenario.overallScore()).isEqualTo(0.85);
    assertThat(scenario.methodNameScore()).isEqualTo(0.90);
    assertThat(scenario.classNameScore()).isEqualTo(0.95);
    assertThat(scenario.parameterNameScore()).isEqualTo(0.80);
    assertThat(scenario.structuralScore()).isEqualTo(1.00);
    assertThat(scenario.cohesionScore()).isEqualTo(0.70);
    assertThat(scenario.issues()).isEmpty();
  }

  @Test
  void parseScenarioWithIssues() {
    var json =
        "{\"version\":\"1.0\",\"scenarios\":["
            + "{\"name\":\"test\",\"overallScore\":0.65"
            + ",\"methodNameScore\":0.70,\"classNameScore\":0.80"
            + ",\"parameterNameScore\":0.50,\"structuralScore\":1.00,\"cohesionScore\":0.60"
            + ",\"issues\":[{\"category\":\"param-name\",\"element\":\"data\""
            + ",\"suggestion\":\"Use a domain-specific name\""
            + ",\"severity\":\"MEDIUM\",\"occurrences\":2,\"impactScore\":4.00}]"
            + "}]}";

    var results = ClarityResultsParser.parse(json);

    assertThat(results.get(0).issues()).hasSize(1);
    var issue = results.get(0).issues().get(0);
    assertThat(issue.category()).isEqualTo("param-name");
    assertThat(issue.element()).isEqualTo("data");
    assertThat(issue.suggestion()).isEqualTo("Use a domain-specific name");
    assertThat(issue.severity()).isEqualTo("MEDIUM");
    assertThat(issue.occurrences()).isEqualTo(2);
    assertThat(issue.impactScore()).isEqualTo(4.00);
  }

  @Test
  void parseSuiteIssuesFromSchema11File() {
    var json =
        "{\"version\":\"1.1\",\"scenarios\":[]"
            + ",\"suiteIssues\":[{\"category\":\"non-canonical-term\""
            + ",\"element\":\"billing.OverdraftService.openAccountWithOverdraft\""
            + ",\"suggestion\":\"use canonical term 'overdraft account'\""
            + ",\"severity\":\"MEDIUM\",\"occurrences\":2,\"impactScore\":4.00}]}";

    var suiteIssues = ClarityResultsParser.parseSuiteIssues(json);

    assertThat(suiteIssues).hasSize(1);
    var issue = suiteIssues.get(0);
    assertThat(issue.category()).isEqualTo("non-canonical-term");
    assertThat(issue.element()).isEqualTo("billing.OverdraftService.openAccountWithOverdraft");
    assertThat(issue.suggestion()).isEqualTo("use canonical term 'overdraft account'");
    assertThat(issue.severity()).isEqualTo("MEDIUM");
    assertThat(issue.occurrences()).isEqualTo(2);
    assertThat(issue.impactScore()).isEqualTo(4.00);
  }

  @Test
  void parseSuiteIssuesToleratesSchema10FileWithoutTheField() {
    var suiteIssues =
        ClarityResultsParser.parseSuiteIssues("{\"version\":\"1.0\",\"scenarios\":[]}");

    assertThat(suiteIssues).isEmpty();
  }

  @Test
  void parseSuiteIssuesThrowsOnMalformedJson() {
    assertThatThrownBy(() -> ClarityResultsParser.parseSuiteIssues("{not valid json"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Failed to parse clarity results");
  }

  @Test
  void parsesSchema12PayloadWithElementsArrays() {
    var json =
        "{\"version\":\"1.2\",\"scenarios\":["
            + "{\"name\":\"Checkout\",\"overallScore\":0.86"
            + ",\"methodNameScore\":0.90,\"classNameScore\":0.95"
            + ",\"parameterNameScore\":0.80,\"structuralScore\":1.00,\"cohesionScore\":0.70"
            + ",\"issues\":[]"
            + ",\"elements\":[{\"kind\":\"method\",\"element\":\"OrderService.processOrder\""
            + ",\"score\":0.62,\"note\":\"Generic verb 'process'\"}]}"
            + "]}";

    var results = ClarityResultsParser.parse(json);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).name()).isEqualTo("Checkout");
    assertThat(results.get(0).overallScore()).isEqualTo(0.86);
    assertThat(results.get(0).issues()).isEmpty();
  }

  @Test
  void parsesUnchangedAcrossSchema10And11And12() {
    var v10 = "{\"version\":\"1.0\",\"scenarios\":[]}";
    var v11 = "{\"version\":\"1.1\",\"scenarios\":[],\"suiteIssues\":[]}";
    var v12 =
        "{\"version\":\"1.2\",\"scenarios\":[{\"name\":\"x\",\"overallScore\":0.9"
            + ",\"methodNameScore\":0.9,\"classNameScore\":0.9,\"parameterNameScore\":0.9"
            + ",\"structuralScore\":0.9,\"cohesionScore\":0.9,\"issues\":[],\"elements\":[]}]}";

    assertThat(ClarityResultsParser.parse(v10)).isEmpty();
    assertThat(ClarityResultsParser.parse(v11)).isEmpty();
    assertThat(ClarityResultsParser.parse(v12)).hasSize(1);
  }

  @Test
  void throwsOnMalformedJson() {
    assertThatThrownBy(() -> ClarityResultsParser.parse("{not valid json"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Failed to parse clarity results");
  }

  @Test
  void throwsOnMissingVersion() {
    assertThatThrownBy(() -> ClarityResultsParser.parse("{\"scenarios\":[]}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing 'version' field");
  }

  @Test
  void throwsOnMissingScenarios() {
    assertThatThrownBy(() -> ClarityResultsParser.parse("{\"version\":\"1.0\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("scenarios");
  }
}
