/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.ConcurrencyInfo;
import ai.narrativetrace.api.event.ConcurrencyKind;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Boundary, malformed-input and sibling-numbering probes for {@link SpanId} and {@link SpanCursor}.
 */
class SpanIdAdversarialP7Test {

  private static final String EN_DASH = "–";

  @Test
  void aRangeOfTwoSiblingsCitesFirstAndLast() {
    assertThat(SpanId.range(List.of("#1.2", "#1.3"))).isEqualTo("#1.2" + EN_DASH + "#1.3");
  }

  @Test
  void stripKeepsTheCarriageReturnOfAWindowsLine() {
    assertThat(SpanId.strip("#1.2 - A.b()\r")).isEqualTo("- A.b()\r");
  }

  @Test
  void stripAndOfAcceptAnIdLongerThanAnyIntegerWithoutOverflow() {
    var huge = "#" + "9".repeat(40) + ".1";

    assertThat(SpanId.strip(huge + " - A.b()")).isEqualTo("- A.b()");
    assertThat(SpanId.of(huge + " - A.b()")).isEqualTo(huge);
  }

  @ParameterizedTest
  @ValueSource(strings = {"#1.", "#.1", "#1..2", "#1.2.", "#1\n", "#1 2", "#1.2\t"})
  void isWellFormedRejectsDanglingOrEmbeddedSeparators(String text) {
    assertThat(SpanId.isWellFormed(text)).as(text).isFalse();
  }

  @Test
  void aCursorNumbersSiblingsUnderItsParentInPlanningOrder() {
    var under = new SpanCursor("#1.2");
    var roots = new SpanCursor(null);

    assertThat(under.next()).isEqualTo("#1.2.1");
    assertThat(under.next()).isEqualTo("#1.2.2");
    assertThat(roots.next()).isEqualTo("#1");
  }

  @Test
  void aForkGroupOfOneMemberTakesOneOrdinaryPosition() {
    var fork = new ConcurrencyInfo("g", "t", 1, false, ConcurrencyKind.FORK_JOIN);
    var siblings = List.of(node("A", "x", fork), node("B", "y", null));

    assertThat(SpanId.idsOf(siblings, null)).containsExactly("#1", "#2");
  }

  @Test
  void twoAdjacentForkGroupsWithDifferentIdsAreNumberedOneAfterTheOther() {
    var g1 = new ConcurrencyInfo("g1", "t", 1, false, ConcurrencyKind.FORK_JOIN);
    var g2 = new ConcurrencyInfo("g2", "t", 1, false, ConcurrencyKind.FORK_JOIN);
    var siblings =
        List.of(node("B", "y", g1), node("A", "z", g1), node("D", "b", g2), node("C", "a", g2));

    assertThat(SpanId.idsOf(siblings, null)).containsExactly("#2", "#1", "#4", "#3");
  }

  @Test
  void everySiblingUnderFireAndForgetWorkGetsItsOwnId() {
    var faf = new ConcurrencyInfo("fanf-1", "bg", 1, false, ConcurrencyKind.FIRE_AND_FORGET);
    var siblings = List.of(node("NotifySvc", "send", faf), node("AuditSvc", "record", faf));

    // SUSPECTED BUG: two fire-and-forget roots of one group share one segment, which takes a
    // single position, so idsOf repeats "#1" for both spans and no id identifies either one.
    assertThat(SpanId.idsOf(siblings, null)).doesNotHaveDuplicates().hasSize(2);
  }

  private static TraceNode node(String type, String method, ConcurrencyInfo info) {
    return new TraceNode(
        new MethodSignature(type, method, List.of()),
        List.of(),
        new TraceOutcome.Returned(null),
        0L,
        0L,
        info);
  }
}
