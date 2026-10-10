/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.tooling.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The grammar check that decides whether a file is a structural trace or something else. */
class StructuralTraceTest {

  @Test
  void acceptsEveryLineShapeTheFormatDefines() {
    assertThat(
            StructuralTrace.looksStructural(
                """
                scenario: Weekend trip settles with three transfers

                - TripSettlementService.recordExpense(tripName, expense)
                  - ExpenseValidator.ensureValid(expense)
                - TripSettlementService.settleTrip(tripName) → value
                  - TripLedger.expensesOf(tripName) !! IllegalStateException
                  ~ fork [2]
                    - BalanceCalculator.computeBalances(expenses) ?? incomplete
                """))
        .isTrue();
  }

  @Test
  void acceptsCallAndMarkerLinesCitedBySpanIds() {
    assertThat(
            StructuralTrace.looksStructural(
                """
                scenario: Weekend trip settles with three transfers

                #1 - TripSettlementService.settleTrip(tripName) → value
                  #1.1 - TripLedger.expensesOf(tripName) !! IllegalStateException
                  ~ fork [2]
                    #1.2 - BalanceCalculator.computeBalances(expenses) ?? incomplete
                  #1.4 ~ fire-and-forget
                    #1.4.1 - Notifier.send(event)
                """))
        .isTrue();
  }

  @Test
  void acceptsSpanIdsMadeOfEveryDigitIncludingZeroAndNine() {
    assertThat(
            StructuralTrace.looksStructural(
                "scenario: s\n\n#10 - OrderService.placeOrder(customerId)\n"
                    + "  #10.9 - Stock.check(sku)\n"))
        .isTrue();
  }

  @Test
  void refusesALineThatIsOnlyAnId() {
    assertThat(StructuralTrace.looksStructural("scenario: s\n\n#1\n  #1.2\n")).isFalse();
  }

  @Test
  void refusesALineWhoseFirstTokenOnlyLooksLikeAnIdAfterItsFirstCharacter() {
    assertThat(
            StructuralTrace.looksStructural(
                "scenario: s\n\nx1 - OrderService.placeOrder(customerId)\n"))
        .isFalse();
  }

  @Test
  void refusesASpanIdThatIsNotADottedNumberPath() {
    for (var id : new String[] {"#", "#1.", "#.1", "#1..2", "#a", "#1:value", "1.1"}) {
      assertThat(
              StructuralTrace.looksStructural(
                  "scenario: s\n\n" + id + " - OrderService.placeOrder(customerId)\n"))
          .as(id)
          .isFalse();
    }
  }

  @Test
  void acceptsAnInvocationHeaderAndAnEmptyParameterList() {
    assertThat(
            StructuralTrace.looksStructural(
                "scenario: Equipment can be found #2\n\n- StockService.check()\n"))
        .isTrue();
  }

  @Test
  void refusesAFileWithNoScenarioHeader() {
    assertThat(StructuralTrace.looksStructural("- OrderService.placeOrder(customerId)\n"))
        .isFalse();
  }

  @Test
  void refusesARenderedNarrativeRenamedToLookLikeOne() {
    assertThat(
            StructuralTrace.looksStructural(
                """
                scenario: Order is placed

                - OrderService.placeOrder(customerId: "C-1234") → "ORD-9001" — 1ms
                """))
        .isFalse();
  }

  @Test
  void refusesAnEmptyFileAndRefusesProse() {
    assertThat(StructuralTrace.looksStructural("")).isFalse();
    assertThat(StructuralTrace.looksStructural("scenario: X\n\nit did not work at all\n"))
        .isFalse();
  }
}
