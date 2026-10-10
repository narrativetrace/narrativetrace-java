/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Edge documents for {@link StructuralDelta}: newline shapes, id shifts and omissions. */
class StructuralDeltaAdversarialP7Test {

  private static final String BASE = "scenario: s\n\n#1 - A.a()\n  #1.1 - B.b()\n  #1.2 - C.c()\n";

  @Test
  void aBaselineWithoutItsFinalNewlineIsUnchanged() {
    // Inverted from the adversarial draft: a final newline is encoding, not structure — the delta
    // must not report a change its own line diff cannot show.
    var delta = StructuralDelta.between("scenario: s\n\n- A.a()\n", "scenario: s\n\n- A.a()");

    assertThat(delta.unchanged()).isTrue();
    assertThat(delta.diff()).isEmpty();
  }

  @Test
  void aBaselineCheckedOutWithCrLfLineEndingsIsUnchanged() {
    // Inverted from the adversarial draft: a Windows checkout (core.autocrlf) turns every LF into
    // CRLF; byte sameness failed every approval run there with a diff of context lines only.
    var delta =
        StructuralDelta.between("scenario: s\r\n\r\n#1 - A.a()\r\n", "scenario: s\n\n#1 - A.a()\n");

    assertThat(delta.unchanged()).isTrue();
    assertThat(delta.diff()).isEmpty();
    assertThat(delta.onlyOmits()).isTrue();
  }

  @Test
  void onlyOmitsTolerancesShiftedIdsOnBothSides() {
    var current = "scenario: s\n\n#1 - A.a()\n  #1.2 - C.c()\n";

    var delta = StructuralDelta.between(BASE, current);

    assertThat(delta.onlyOmits()).isTrue();
    assertThat(delta.summary()).isEqualTo("-1 call B.b");
  }

  @Test
  void onlyOmitsIsFalseWhenTheCurrentRunAddsALineWithAnId() {
    var current = "scenario: s\n\n#1 - A.a()\n  #1.1 - X.x()\n  #1.2 - B.b()\n";

    var baseline = "scenario: s\n\n#1 - A.a()\n  #1.1 - B.b()\n";

    assertThat(StructuralDelta.between(baseline, current).onlyOmits()).isFalse();
  }

  @Test
  void onlyOmitsIsFalseWhenTwoLinesAreReorderedRatherThanOmitted() {
    var current = "scenario: s\n\n#1 - A.a()\n  #1.1 - C.c()\n  #1.2 - B.b()\n";

    assertThat(StructuralDelta.between(BASE, current).onlyOmits()).isFalse();
  }

  @Test
  void aForkGroupsMembersAreCountedAsCallsButTheMarkerIsNot() {
    var current =
        BASE + "  ~ fork [2]\n" + "    #1.3 - Pay.charge()\n" + "    #1.4 - Pay.charge()\n";

    assertThat(StructuralDelta.between(BASE, current).summary()).isEqualTo("+2 calls Pay.charge");
  }

  @Test
  void withoutIdsStripsEveryIdLineAndKeepsIdFreeLinesAsTheyWere() {
    assertThat(StructuralDelta.withoutIds("#1 - A\n  #1.1 - B\n  ~ fork [2]\nplain"))
        .isEqualTo("- A\n  - B\n  ~ fork [2]\nplain");
  }

  static java.util.List<String> markedLines(String diff) {
    return Arrays.stream(diff.split("\n"))
        .filter(line -> line.startsWith("+") || line.startsWith("-"))
        .toList();
  }
}
