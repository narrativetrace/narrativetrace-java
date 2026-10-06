/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The runner's decision, the twin of buildSrc's {@code ContractDecisionSupport} (the two cannot
 * share code: this project consumes only registry artifacts and can never depend on the root
 * build's {@code buildSrc}). Both sides are unit tested against the same cases, so "would the gate
 * have fired" answers the same here and there.
 */
class ContractRunnerTest {

  private static ContractEntry entry(String expect, String coordinate) {
    return new ContractEntry(
        "probed-output-default",
        "probed-default",
        "documentation/configuration-guide.md#an-anchor",
        "narrativetrace.output defaults to true",
        expect,
        "contract-probe/src/main/java/ai/narrativetrace/contract/probes/OutputDefaultProbe.java",
        coordinate,
        null);
  }

  @Test
  void holdsWhenTheProbeObservedExactlyWhatTheEntryDocuments() {
    assertEquals(ContractRunner.HOLDS, ContractRunner.verdictOf(entry("true", null), "true"));
  }

  @Test
  void failsWhenTheProbeObservedSomethingElse() {
    assertEquals(ContractRunner.FAILS, ContractRunner.verdictOf(entry("true", null), "false"));
  }

  @Test
  void failsWhenTheProbeCouldNotAnswerAtAll() {
    // A probe returns null when it could not even run (registry unreachable, artifact missing).
    // That is a failure with its own explaining detail, never a silent skip.
    assertEquals(ContractRunner.FAILS, ContractRunner.verdictOf(entry("true", null), null));
  }

  @Test
  void failsOnAValueThatOnlyDiffersInCase() {
    assertEquals(ContractRunner.FAILS, ContractRunner.verdictOf(entry("PRESENT", null), "present"));
  }

  @Test
  void theVerdictSetIsHoldsAndFails() {
    // There is no third verdict. An entry describes the code it was committed with, and main IS
    // the published code — the snapshot and the artifacts go out from one commit — so no entry can
    // ever be "not released yet".
    Set<String> verdicts =
        Stream.of("true", "false", null)
            .map(observed -> ContractRunner.verdictOf(entry("true", null), observed))
            .collect(Collectors.toSet());

    assertEquals(Set.of(ContractRunner.HOLDS, ContractRunner.FAILS), verdicts);
  }

  @Test
  void theFailureMessageNamesTheEntryTheExpectationTheArtifactAndWhatWasRead() {
    String message =
        ContractRunner.failureMessage(
            entry("true", "ai.narrativetrace:narrativetrace-core"), "0.2.4", "false");

    assertTrue(message.contains("probed-output-default"), message);
    assertTrue(message.contains("\"true\""), message);
    assertTrue(message.contains("ai.narrativetrace:narrativetrace-core"), message);
    assertTrue(message.contains("0.2.4"), message);
    assertTrue(message.contains("\"false\""), message);
  }

  @Test
  void theFailureMessageNeverTalksAboutWhenTheClaimBegan() {
    String message = ContractRunner.failureMessage(entry("true", null), "0.2.4", "false");

    assertTrue(!message.contains("since"), message);
  }

  @Test
  void theFailureMessageFallsBackToTheEntryIdWhenThereIsNoCoordinate() {
    String message = ContractRunner.failureMessage(entry("true", null), "0.2.4", "false");

    assertTrue(message.contains("probed-output-default 0.2.4 (published)"), message);
  }

  @Test
  void theFailureMessageSaysNoAnswerWhenTheProbeReturnedNull() {
    String message = ContractRunner.failureMessage(entry("true", null), "0.2.4", null);

    assertTrue(message.contains("<no answer>"), message);
  }

  @Test
  void everyEntryIdInTheRealContractHasExactlyOneDispatch() {
    // Pairs with ContractDispatchCoverageTest, which reads the contract file; this one pins that
    // the dispatch map itself holds no duplicate key that a later merge could introduce.
    List<String> dispatched = List.copyOf(ContractRunner.dispatchedEntryIds());

    assertEquals(dispatched.size(), Set.copyOf(dispatched).size());
  }
}
