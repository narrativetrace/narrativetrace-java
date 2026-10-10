/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.ConcurrencyInfo;
import ai.narrativetrace.api.event.ConcurrencyKind;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The span id grammar and its derivation from position. */
class SpanIdTest {

  @Test
  void aRootIsItsPositionAndAChildExtendsItsParentsPath() {
    assertThat(SpanId.child(null, 3)).isEqualTo("#3");
    assertThat(SpanId.child("#1.3", 2)).isEqualTo("#1.3.2");
  }

  @Test
  void stripRemovesTheIdAfterTheIndentAndKeepsTheIndent() {
    assertThat(SpanId.strip("    #1.12.3 - A.b()")).isEqualTo("    - A.b()");
    assertThat(SpanId.strip("#7 ~ fire-and-forget")).isEqualTo("~ fire-and-forget");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "- A.b()",
        "  ~ fork [2]",
        "#",
        "# - A.b()",
        "#1. - A.b()",
        "#.1 - A.b()",
        "#1..2 - A.b()",
        "#1a - A.b()",
        "#1",
        "#1\t- A.b()",
        "\t#1 - A.b()",
        "",
        "scenario: #1 rocks"
      })
  void aLineWithoutAWellFormedIdIsLeftAsWrittenAndCitesNothing(String line) {
    assertThat(SpanId.strip(line)).isEqualTo(line);
    assertThat(SpanId.of(line)).isNull();
  }

  @Test
  void ofReadsTheIdALineOpensWith() {
    assertThat(SpanId.of("  #1.2 - A.b()")).isEqualTo("#1.2");
  }

  @Test
  void wellFormedIsExactlyOneId() {
    assertThat(SpanId.isWellFormed("#1.20.3")).isTrue();
    assertThat(SpanId.isWellFormed("#1.2 ")).isFalse();
    assertThat(SpanId.isWellFormed(" #1.2")).isFalse();
    assertThat(SpanId.isWellFormed("#1.2#3")).isFalse();
    assertThat(SpanId.isWellFormed("")).isFalse();
    assertThat(SpanId.isWellFormed(null)).isFalse();
  }

  @Test
  void aRangeCitesFirstAndLastOrTheOneId() {
    assertThat(SpanId.range(List.of("#1.2", "#1.3", "#1.4"))).isEqualTo("#1.2–#1.4");
    assertThat(SpanId.range(List.of("#1.2"))).isEqualTo("#1.2");
    assertThatThrownBy(() -> SpanId.range(List.of())).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void idsOfNumbersAForkGroupBySignatureButListsItInCaptureOrder() {
    var info = new ConcurrencyInfo("g", "t", 1, false, ConcurrencyKind.FORK_JOIN);
    var faf = new ConcurrencyInfo("f", "t", 1, false, ConcurrencyKind.FIRE_AND_FORGET);
    var siblings =
        List.of(
            node("Zed", "a", null),
            node("Delta", "z", info),
            node("Bravo", "z", info),
            node("Delta", "a", info),
            node("Launcher", "go", faf),
            node("Alpha", "a", null));

    assertThat(SpanId.idsOf(siblings, "#4"))
        .containsExactly("#4.1", "#4.4", "#4.2", "#4.3", "#4.5", "#4.6");
  }

  @Test
  void idsOfAnEmptyListIsEmpty() {
    assertThat(SpanId.idsOf(List.of(), null)).isEmpty();
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
