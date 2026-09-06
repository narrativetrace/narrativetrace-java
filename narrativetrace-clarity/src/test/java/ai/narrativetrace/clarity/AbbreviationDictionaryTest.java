/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AbbreviationDictionaryTest {

  private final AbbreviationDictionary dictionary = new AbbreviationDictionary();

  @Test
  void recognizesUniversalAbbreviation() {
    var result = dictionary.lookup("url");
    assertThat(result).isNotNull();
    assertThat(result.tier()).isEqualTo(AbbreviationDictionary.Tier.UNIVERSAL);
    assertThat(result.score()).isEqualTo(0.8);
    assertThat(result.expansion()).isEqualTo("uniform resource locator");
  }

  @Test
  void recognizesWellKnownAbbreviation() {
    var result = dictionary.lookup("ctx");
    assertThat(result).isNotNull();
    assertThat(result.tier()).isEqualTo(AbbreviationDictionary.Tier.WELL_KNOWN);
    assertThat(result.score()).isEqualTo(0.6);
    assertThat(result.expansion()).isEqualTo("context");
  }

  @Test
  void recognizesAmbiguousAbbreviation() {
    var result = dictionary.lookup("proc");
    assertThat(result).isNotNull();
    assertThat(result.tier()).isEqualTo(AbbreviationDictionary.Tier.AMBIGUOUS);
    assertThat(result.score()).isEqualTo(0.3);
  }

  @Test
  void returnsNullForFullWord() {
    var result = dictionary.lookup("calculate");
    assertThat(result).isNull();
  }

  @Test
  void isCaseInsensitive() {
    var result = dictionary.lookup("URL");
    assertThat(result).isNotNull();
    assertThat(result.tier()).isEqualTo(AbbreviationDictionary.Tier.UNIVERSAL);
  }

  @Test
  void recognizesPlatformAndReliabilityAbbreviations() {
    assertThat(dictionary.lookup("ttl")).isNotNull();
    assertThat(dictionary.lookup("ttl").tier()).isEqualTo(AbbreviationDictionary.Tier.WELL_KNOWN);
    assertThat(dictionary.lookup("slo")).isNotNull();
    assertThat(dictionary.lookup("slo").expansion()).isEqualTo("service level objective");
  }

  @Test
  void recognizesSecurityAndComplianceAbbreviations() {
    assertThat(dictionary.lookup("mfa")).isNotNull();
    assertThat(dictionary.lookup("mfa").tier()).isEqualTo(AbbreviationDictionary.Tier.UNIVERSAL);
    assertThat(dictionary.lookup("gdpr")).isNotNull();
    assertThat(dictionary.lookup("gdpr").tier()).isEqualTo(AbbreviationDictionary.Tier.WELL_KNOWN);
  }

  @Test
  void recognizesAiAbbreviationsAndAmbiguity() {
    assertThat(dictionary.lookup("llm")).isNotNull();
    assertThat(dictionary.lookup("llm").tier()).isEqualTo(AbbreviationDictionary.Tier.WELL_KNOWN);
    assertThat(dictionary.lookup("rag")).isNotNull();
    assertThat(dictionary.lookup("rag").tier()).isEqualTo(AbbreviationDictionary.Tier.AMBIGUOUS);
  }

  @Test
  void listedShorthandStopsBeingAnAbbreviation() {
    var projectDictionary =
        new AbbreviationDictionary(
            DomainVocabulary.of(Set.of(), Set.of(), Map.of("acc", "account", "fx", "forex")));

    assertThat(projectDictionary.lookup("acc")).isNull();
    assertThat(projectDictionary.lookup("fx")).isNull();
    assertThat(dictionary.lookup("acc")).isNotNull();
  }

  @Test
  void listedShorthandIsSpelledOutFromItsDeclaredExpansion() {
    var projectDictionary =
        new AbbreviationDictionary(
            DomainVocabulary.of(Set.of(), Set.of(), Map.of("fx", "foreign exchange")));

    assertThat(projectDictionary.projectExpansionOf("FX")).isEqualTo("foreign exchange");
    assertThat(projectDictionary.projectExpansionOf("mgr")).isNull();
  }

  @Test
  void aTokenDeclaredOnlyAsATermIsNotAcceptedShorthand() {
    var asVerb = new AbbreviationDictionary(DomainVocabulary.of(Set.of("calc"), Set.of()));
    var asNoun = new AbbreviationDictionary(DomainVocabulary.of(Set.of(), Set.of("calc")));

    assertThat(asVerb.lookup("calc"))
        .as("committing a term is not a decision to accept the shorthand spelling of it")
        .isNotNull();
    assertThat(asNoun.lookup("calc")).isNotNull();
  }

  @Test
  void unlistedAbbreviationsStayPenalized() {
    var projectDictionary =
        new AbbreviationDictionary(
            DomainVocabulary.of(Set.of(), Set.of(), Map.of("acc", "account")));

    assertThat(projectDictionary.lookup("mgr")).isNotNull();
    assertThat(projectDictionary.lookup("mgr").tier())
        .isEqualTo(AbbreviationDictionary.Tier.WELL_KNOWN);
  }

  @Test
  void acceptedShorthandMatchesCaseInsensitively() {
    var projectDictionary =
        new AbbreviationDictionary(
            DomainVocabulary.of(Set.of(), Set.of(), Map.of("Acc", "account")));

    assertThat(projectDictionary.lookup("ACC")).isNull();
    assertThat(projectDictionary.projectExpansionOf("ACC")).isEqualTo("account");
  }

  @Test
  void rejectsNullVocabulary() {
    assertThatIllegalArgumentException().isThrownBy(() -> new AbbreviationDictionary(null));
  }
}
