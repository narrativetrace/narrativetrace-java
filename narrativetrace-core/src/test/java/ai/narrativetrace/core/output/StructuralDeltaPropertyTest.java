/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.render.SpanId;
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
 * a document never differs from itself, unchanged is line equality once span ids are set aside (an
 * id is derived from position, so a baseline written before ids existed still compares), the diff
 * faithfully reconstructs both sides, and a change never yields an empty summary.
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
  void unchangedIsLineEqualityOnceSpanIdsAreSetAside(
      @ForAll @From("documents") String baseline, @ForAll @From("documents") String current) {
    assertThat(StructuralDelta.between(baseline, current).unchanged())
        .isEqualTo(
            StructuralDelta.withoutIds(baseline).equals(StructuralDelta.withoutIds(current)));
  }

  @Property
  void spanIdsAloneNeverMakeAChange(
      @ForAll @From("documents") String document, @ForAll("ids") List<String> ids) {
    var lines = document.split("\n", -1);
    var real = document.endsWith("\n") ? lines.length - 1 : lines.length;
    for (var i = 0; i < real && i < ids.size(); i++) {
      var indent = 0;
      while (indent < lines[i].length() && lines[i].charAt(indent) == ' ') {
        indent++;
      }
      lines[i] = lines[i].substring(0, indent) + ids.get(i) + " " + lines[i].substring(indent);
    }

    assertThat(StructuralDelta.between(document, String.join("\n", lines)).unchanged()).isTrue();
  }

  @Provide
  Arbitrary<List<String>> ids() {
    return Arbitraries.integers()
        .between(1, 99)
        .list()
        .ofMinSize(1)
        .ofMaxSize(4)
        .map(path -> "#" + path.stream().map(String::valueOf).collect(Collectors.joining(".")))
        .list()
        .ofMaxSize(10);
  }

  @Property
  void diffReconstructsBothDocuments(
      @ForAll @From("documents") String baseline, @ForAll @From("documents") String current) {
    Assume.that(!StructuralDelta.between(baseline, current).unchanged());

    var diff = StructuralDelta.between(baseline, current).diff();

    assertThat(diffLines(diff, ' ', '-').stream().map(SpanId::strip).toList())
        .isEqualTo(baseline.lines().map(SpanId::strip).toList());
    assertThat(diffLines(diff, ' ', '+').stream().map(StructuralDeltaPropertyTest::unannotated))
        .isEqualTo(current.lines().toList());
  }

  @Property
  void aChangeNeverSummarizesAsNothing(
      @ForAll @From("documents") String baseline, @ForAll @From("documents") String current) {
    Assume.that(!StructuralDelta.between(baseline, current).unchanged());

    assertThat(StructuralDelta.between(baseline, current).summary()).isNotEmpty();
  }

  /** A context line without the {@code (was #id)} note the diff adds when a span id shifted. */
  private static String unannotated(String line) {
    var note = line.lastIndexOf("  (was #");
    return note < 0 ? line : line.substring(0, note);
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
