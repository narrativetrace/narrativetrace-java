/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/** Subsequence and keyed-matching edge cases of {@link LineDiff}. */
class LineDiffAdversarialP7Test {

  @Test
  void aDuplicatedBaselineLineMayBeMatchedOnceWhileExtraCopiesFail() {
    assertThat(LineDiff.isSubsequence("x\nx\ny\n", "x\ny\n")).isTrue();
    assertThat(LineDiff.isSubsequence("x\nx\ny\n", "x\nx\nx\n")).isFalse();
  }

  @Test
  void aNonEmptyCurrentIsNeverASubsequenceOfAnEmptyBaseline() {
    assertThat(LineDiff.isSubsequence("", "a\n")).isFalse();
  }

  @Test
  void keyedMatchingTreatsLinesWithEqualKeysAsContextWhateverTheirBytes() {
    var out =
        LineDiff.unified(
            "#1 - A\n", "#9 - A\n", line -> line.substring(line.indexOf('-')), (was, now) -> now);

    assertThat(out).isEqualTo(" #9 - A\n");
  }

  @Test
  void aReplacedLineShowsTheRemovalBeforeTheInsertion() {
    assertThat(LineDiff.unified("a\n", "b\n", UnaryOperator.identity(), (was, now) -> now))
        .isEqualTo("-a\n+b\n");
  }
}
