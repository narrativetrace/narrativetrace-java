/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The structural delta between two {@code .nt} artifacts: the engine behind the post-run console
 * delta line, the failure delta against last green, and approval mode. Sameness is byte equality of
 * the artifact ({@code StructuralTraceRenderer} guarantees determinism), so an unchanged scenario
 * is provable and any difference is real behavior change worth surfacing.
 */
class StructuralDeltaTest {

  private static final String DOCUMENT =
      """
      scenario: Weekend trip settles with three transfers

      - TripSettlementService.recordExpense(tripName, expense)
        - ExpenseValidator.ensureValid(expense)
        - TripLedger.recordExpense(tripName, expense)
      """;

  private static final String WITH_IDS =
      """
      scenario: Weekend trip settles with three transfers

      #1 - TripSettlementService.recordExpense(tripName, expense)
        #1.1 - ExpenseValidator.ensureValid(expense)
        #1.2 - TripLedger.recordExpense(tripName, expense)
      """;

  @Test
  void aBaselineWrittenBeforeSpanIdsStillComparesUnchanged() {
    var delta = StructuralDelta.between(DOCUMENT, WITH_IDS);

    assertThat(delta.unchanged()).isTrue();
    assertThat(delta.summary()).isEmpty();
    assertThat(delta.diff()).isEmpty();
  }

  @Test
  void theDiffCitesEachSideByItsOwnSpanIds() {
    var current =
        WITH_IDS
            .replace(
                "  #1.1 - ExpenseValidator.ensureValid(expense)\n",
                "  #1.1 - PolicyEngine.approve(expense) → value\n"
                    + "  #1.2 - ExpenseValidator.ensureValid(expense)\n")
            .replace("  #1.2 - TripLedger", "  #1.3 - TripLedger");

    var delta = StructuralDelta.between(WITH_IDS, current);

    assertThat(delta.diff())
        .isEqualTo(
            """
             scenario: Weekend trip settles with three transfers
            \s
             #1 - TripSettlementService.recordExpense(tripName, expense)
            +  #1.1 - PolicyEngine.approve(expense) → value
               #1.2 - ExpenseValidator.ensureValid(expense)  (was #1.1)
               #1.3 - TripLedger.recordExpense(tripName, expense)  (was #1.2)
            """);
  }

  @Test
  void byteIdenticalDocumentsAreUnchanged() {
    var delta = StructuralDelta.between(DOCUMENT, DOCUMENT);

    assertThat(delta.unchanged()).isTrue();
  }

  @Test
  void addedCallsSummarizeAsPlusCountPerSignature() {
    var current =
        DOCUMENT + "    - CurrencyConverter.toBaseCurrency(amount, currency) → value\n".repeat(4);

    var delta = StructuralDelta.between(DOCUMENT, current);

    assertThat(delta.unchanged()).isFalse();
    assertThat(delta.summary()).isEqualTo("+4 calls CurrencyConverter.toBaseCurrency");
  }

  @Test
  void removedCallSummarizesAsMinusWithSingularNoun() {
    var current = DOCUMENT.replace("  - TripLedger.recordExpense(tripName, expense)\n", "");

    var delta = StructuralDelta.between(DOCUMENT, current);

    assertThat(delta.summary()).isEqualTo("-1 call TripLedger.recordExpense");
  }

  @Test
  void sameCallCountsButDifferentShapeFallsBackToStructureChanged() {
    var current =
        DOCUMENT.replace(
            "  - TripLedger.recordExpense(tripName, expense)\n",
            "  - TripLedger.recordExpense(tripName, expense) !! LedgerFullException\n");

    var delta = StructuralDelta.between(DOCUMENT, current);

    assertThat(delta.unchanged()).isFalse();
    assertThat(delta.summary()).isEqualTo("structure changed");
  }

  @Test
  void nullDocumentsAreRejected() {
    assertThatThrownBy(() -> StructuralDelta.between(null, DOCUMENT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("baseline must not be null");
    assertThatThrownBy(() -> StructuralDelta.between(DOCUMENT, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("current must not be null");
  }

  @Test
  void diffMarksRemovedAndAddedLinesAroundUnchangedContext() {
    var current =
        DOCUMENT.replace(
            "  - ExpenseValidator.ensureValid(expense)\n",
            "  - PolicyEngine.approve(expense) → value\n");

    var delta = StructuralDelta.between(DOCUMENT, current);

    assertThat(delta.diff())
        .isEqualTo(
            """
             scenario: Weekend trip settles with three transfers
            \s
             - TripSettlementService.recordExpense(tripName, expense)
            -  - ExpenseValidator.ensureValid(expense)
            +  - PolicyEngine.approve(expense) → value
               - TripLedger.recordExpense(tripName, expense)
            """);
  }

  @Test
  void mixedChangesListAddedSignaturesBeforeRemovedOnes() {
    var current =
        DOCUMENT.replace(
            "  - ExpenseValidator.ensureValid(expense)\n",
            "  - PolicyEngine.approve(expense) → value\n");

    var delta = StructuralDelta.between(DOCUMENT, current);

    assertThat(delta.summary())
        .isEqualTo("+1 call PolicyEngine.approve, -1 call ExpenseValidator.ensureValid");
  }

  @Test
  void forkMarkersAreShapeNotCalls() {
    var current =
        DOCUMENT.replace(
            "  - ExpenseValidator.ensureValid(expense)\n"
                + "  - TripLedger.recordExpense(tripName, expense)\n",
            "  ~ fork [2]\n"
                + "    - ExpenseValidator.ensureValid(expense)\n"
                + "    - TripLedger.recordExpense(tripName, expense)\n");

    var delta = StructuralDelta.between(DOCUMENT, current);

    assertThat(delta.unchanged()).isFalse();
    assertThat(delta.summary()).isEqualTo("structure changed");
  }
}
