/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Invariants of the structural delta over arbitrary LF documents (the {@code .nt} spec is LF-only):
 * a document never differs from itself, unchanged is exactly byte equality, the diff faithfully
 * reconstructs both sides, and a change never yields an empty summary.
 */
class StructuralDeltaPropertyTest {

  @Property
  void aDocumentNeverDiffersFromItself(@ForAll @From("documents") String document) {
    var delta = StructuralDelta.between(document, document);

    assertThat(delta.unchanged()).isTrue();
    assertThat(delta.summary()).isEmpty();
    assertThat(delta.diff()).isEmpty();
  }

  @Property
  void unchangedIsExactlyByteEquality(
      @ForAll @From("documents") String baseline, @ForAll @From("documents") String current) {
    assertThat(StructuralDelta.between(baseline, current).unchanged())
        .isEqualTo(baseline.equals(current));
  }

  @Property
  void diffReconstructsBothDocuments(
      @ForAll @From("documents") String baseline, @ForAll @From("documents") String current) {
    Assume.that(!baseline.equals(current));

    var diff = StructuralDelta.between(baseline, current).diff();

    assertThat(diffLines(diff, ' ', '-')).isEqualTo(baseline.lines().toList());
    assertThat(diffLines(diff, ' ', '+')).isEqualTo(current.lines().toList());
  }

  @Property
  void aChangeNeverSummarizesAsNothing(
      @ForAll @From("documents") String baseline, @ForAll @From("documents") String current) {
    Assume.that(!baseline.equals(current));

    assertThat(StructuralDelta.between(baseline, current).summary()).isNotEmpty();
  }

  private static List<String> diffLines(String diff, char keep, char alsoKeep) {
    return diff.lines()
        .filter(line -> !line.isEmpty() && (line.charAt(0) == keep || line.charAt(0) == alsoKeep))
        .map(line -> line.substring(1))
        .collect(Collectors.toList());
  }

  @Provide
  Arbitrary<String> documents() {
    var callLine =
        Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(8)
            .tuple2()
            .map(t -> "- " + t.get1() + "." + t.get2() + "(arg)");
    var anyLine = Arbitraries.strings().ascii().excludeChars('\n', '\r').ofMaxLength(20);
    var line = Arbitraries.oneOf(callLine, anyLine);
    return line.list()
        .ofMaxSize(10)
        .map(lines -> lines.isEmpty() ? "" : String.join("\n", lines) + "\n");
  }
}
