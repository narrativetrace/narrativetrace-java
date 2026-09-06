/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Guard-clause contracts of the harvest/merge value records. */
class HarvestRecordContractTest {

  @Test
  void harvestCandidateRejectsInvalidComponents() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new HarvestCandidate(" ", "p", TermKind.WORD, "s", "i", 1))
        .withMessageContaining("context");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new HarvestCandidate("c", null, TermKind.WORD, "s", "i", 1))
        .withMessageContaining("phrase");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new HarvestCandidate("c", "p", null, "s", "i", 1))
        .withMessageContaining("kind");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new HarvestCandidate("c", "p", TermKind.WORD, "", "i", 1))
        .withMessageContaining("site");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new HarvestCandidate("c", "p", TermKind.WORD, "s", " ", 1))
        .withMessageContaining("identifier");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new HarvestCandidate("c", "p", TermKind.WORD, "s", "i", 0))
        .withMessageContaining("occurrences");
  }

  @Test
  void harvestResultRejectsNullCandidateList() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new HarvestResult(null))
        .withMessageContaining("candidates");
  }

  @Test
  void mergeResultRejectsNullComponents() {
    var glossary = new Glossary(1, Map.of(), List.of());
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new MergeResult(null, List.of(), List.of()))
        .withMessageContaining("glossary");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new MergeResult(glossary, null, List.of()))
        .withMessageContaining("newTerms");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new MergeResult(glossary, List.of(), null))
        .withMessageContaining("suppressedAliasUses");
  }

  @Test
  void normalizerCandidateRejectsInvalidComponents() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new TermNormalizer.Candidate(" ", TermKind.WORD))
        .withMessageContaining("phrase");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new TermNormalizer.Candidate("p", null))
        .withMessageContaining("kind");
  }

  @Test
  void normalizerRejectsBlankInputsOnEveryEntryPoint() {
    var normalizer = new TermNormalizer();
    assertThatIllegalArgumentException()
        .isThrownBy(() -> normalizer.methodCandidates(" "))
        .withMessageContaining("identifier");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> normalizer.parameterCandidate(null))
        .withMessageContaining("identifier");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> normalizer.classCandidate(""))
        .withMessageContaining("identifier");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> normalizer.exceptionCandidate(" "))
        .withMessageContaining("identifier");
  }
}
