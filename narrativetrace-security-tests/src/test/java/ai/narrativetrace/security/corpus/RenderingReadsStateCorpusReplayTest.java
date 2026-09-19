/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.render.ValueRenderer;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Corpus replay for the {@code graphs.json} rows added for the rendering rule copied into the
 * repository's agent guide (owner ruling 2026-09-17, "rendering reads state, never runs behaviour",
 * and its 2026-09-18 refinement: a user subclass of an ABSTRACT platform base is
 * object-introspected, its override NEVER called): {@code record-accessor-with-counter}, {@code
 * platform-collection-side-effecting-iterator}, {@code lookalike-collection-not-platform-defined},
 * {@code abstract-map-subclass-override} and {@code abstract-collection-subclass-override}. Each
 * row's expected outcome is "rendered without executing" — the renderer must never invoke the
 * fixture's own overridden member.
 *
 * <p>INTENT: {@link HostileCorpusTest#everyDeclaredGraphShapeBuilds} and {@link
 * ai.narrativetrace.security.CapturePathGraphConformanceTest} already replay every row in {@code
 * check} for containment and well-formedness — those pass for all five rows today (a thrown
 * override degrades to the typed failure marker, which leaks nothing). What neither checks is
 * whether the override ran at all, which is the property these rows exist for. All five are live
 * regression guards now that the abstract-base refinement has landed — mirrors land the same five
 * ids in every other runtime's corpus copy, per the cross-port rule.
 *
 * <p><b>@llmNote</b> Three further rows close the gaps the abstract-base refinement left open:
 * {@code structured-path-user-collection-not-enumerated}, {@code
 * fieldless-abstract-subclass-tostring-door} and {@code fieldless-sidetable-tostring-door} pin the
 * three ways the rule used to be escaped — the STRUCTURED path enumerating any {@code
 * Collection}/{@code Map} unconditionally (origin-blind), a fieldless abstract-base subclass
 * reaching its override through the inherited {@code toString()}, and a fieldless class of no
 * composite kind at all holding its state in a static identity-keyed side table that its own {@code
 * toString()} reads. All three are live regression guards now that all three are closed.
 *
 * <p><b>@llmNote</b> {@code number-subclass-tostring-door} closes the fourth: a user {@code Number}
 * subclass reached the flat renderer's scalar-numeric fast path, which read its own text while the
 * structured path walked the same value's fields — a leak and a channel split at once. The row is
 * asserted on both channels here, and the routing parity replay now witnesses whether the deny-list
 * withheld anything, so a split of that kind fails on every row rather than only where a test
 * happened to look.
 */
class RenderingReadsStateCorpusReplayTest {

  private final ValueRenderer renderer = new ValueRenderer();

  @Test
  void recordAccessorWithCounterRowRendersWithoutInvokingTheAccessor() {
    var calls = new AtomicInteger();
    var fixture = new HostileMembers.CountingAccessor(HostileGraphs.secret("sentinel"), calls);

    renderer.render(fixture);

    assertThat(calls).as("the accessor must never run").hasValue(0);
  }

  @Test
  void platformCollectionSideEffectingIteratorRowRendersWithoutExecutingTheOverride() {
    var fixture = new HostileMembers.SideEffectingIteratorList(HostileGraphs.secret("sentinel"));

    renderer.render(fixture);

    assertThat(fixture.iteratorCalls).as("the overridden iterator() must never run").hasValue(0);
  }

  @Test
  void lookalikeCollectionRowRendersWithoutExecutingItsOwnIterator() {
    var fixture = new HostileMembers.LookalikeCollection(HostileGraphs.secret("sentinel"));

    renderer.render(fixture);

    assertThat(fixture.iteratorCalls).as("the hand-rolled iterator() must never run").hasValue(0);
  }

  @Test
  void abstractMapSubclassOverrideRowRendersWithoutExecutingTheOverride() {
    var fixture = new HostileMembers.AbstractMapSubclassOverride(HostileGraphs.secret("sentinel"));

    renderer.render(fixture);

    assertThat(fixture.entrySetCalls).as("the overridden entrySet() must never run").hasValue(0);
  }

  @Test
  void abstractCollectionSubclassOverrideRowRendersWithoutExecutingTheOverride() {
    var fixture =
        new HostileMembers.AbstractCollectionSubclassOverride(HostileGraphs.secret("sentinel"));

    renderer.render(fixture);

    assertThat(fixture.iteratorCalls).as("the overridden iterator() must never run").hasValue(0);
  }

  /**
   * Gap 1 — {@code renderStructuredComplex} enumerates any {@code Collection} unconditionally,
   * origin-blind, so the structured path calls this hand-rolled type's own {@code iterator()} even
   * though the flat path (pinned above by {@link
   * #lookalikeCollectionRowRendersWithoutExecutingItsOwnIterator}) already does not.
   */
  @Test
  void lookalikeCollectionRowIsNeverEnumeratedByStructuredRenderingEither() {
    var fixture = new HostileMembers.LookalikeCollection(HostileGraphs.secret("sentinel"));

    renderer.renderStructured(fixture);

    assertThat(fixture.iteratorCalls)
        .as("the structured path must never call the hand-rolled iterator() either")
        .hasValue(0);
  }

  /**
   * Gap 2 — a FIELDLESS {@code AbstractCollection} subclass was trusted to stand behind its own
   * text because it declares no field, but the text it was trusted for is the inherited {@code
   * AbstractCollection.toString()}, which walks {@code iterator()} internally. The flat path
   * reaches this door too — bullets 11/12 in {@code RenderingReadsStateNeverRunsBehaviourTest} only
   * ever pinned the field-bearing sibling, which {@code rendersItsOwnString} already distrusts for
   * an unrelated reason.
   */
  @Test
  void fieldlessAbstractSubclassToStringDoorRowRendersWithoutExecutingTheOverride() {
    HostileMembers.FieldlessAbstractSubclassToStringDoor.ITERATOR_CALLS.set(0);
    var fixture = new HostileMembers.FieldlessAbstractSubclassToStringDoor();

    renderer.render(fixture);

    assertThat(HostileMembers.FieldlessAbstractSubclassToStringDoor.ITERATOR_CALLS)
        .as(
            "the overridden iterator() must never run, even reached through the inherited"
                + " toString()")
        .hasValue(0);
  }

  /**
   * Same gap 2 door, replayed on the structured path — both paths share {@code
   * rendersItsOwnString}.
   */
  @Test
  void fieldlessAbstractSubclassToStringDoorRowRendersWithoutExecutingTheOverrideStructured() {
    HostileMembers.FieldlessAbstractSubclassToStringDoor.ITERATOR_CALLS.set(0);
    var fixture = new HostileMembers.FieldlessAbstractSubclassToStringDoor();

    renderer.renderStructured(fixture);

    assertThat(HostileMembers.FieldlessAbstractSubclassToStringDoor.ITERATOR_CALLS)
        .as(
            "the overridden iterator() must never run, even reached through the inherited"
                + " toString()")
        .hasValue(0);
  }

  /**
   * Gap 3, the {@code fieldless-sidetable-tostring-door} row: a fieldless class that is no
   * composite at all, holding its state in a static identity-keyed side table. Emptiness is not
   * statelessness — the text such a class is trusted for reads the table — so its own {@code
   * toString()} must not be entered on either path, and the rendering must still name the type
   * rather than fall silent or degrade to a failure marker.
   */
  @Test
  void fieldlessSideTableToStringDoorRowNeverConsultsTheSideTable() {
    HostileMembers.FieldlessSideTableToStringDoor.SIDE_TABLE_READS.set(0);
    var sentinel = "sentinel-side-table";
    var fixture = new HostileMembers.FieldlessSideTableToStringDoor(HostileGraphs.secret(sentinel));

    var rendered = renderer.render(fixture);

    assertThat(HostileMembers.FieldlessSideTableToStringDoor.SIDE_TABLE_READS)
        .as("the side table must never be read, which means toString() was never entered")
        .hasValue(0);
    assertThat(rendered)
        .as("the type is still named, and no failure marker stands in for it")
        .contains("FieldlessSideTableToStringDoor")
        .doesNotContain(sentinel)
        .doesNotContain("<error");
  }

  /** Same gap 3 door on the structured path: one decision, shared, so the two cannot drift. */
  @Test
  void fieldlessSideTableToStringDoorRowNeverConsultsTheSideTableStructured() {
    HostileMembers.FieldlessSideTableToStringDoor.SIDE_TABLE_READS.set(0);
    var sentinel = "sentinel-side-table-structured";
    var fixture = new HostileMembers.FieldlessSideTableToStringDoor(HostileGraphs.secret(sentinel));

    var rendered = String.valueOf(renderer.renderStructured(fixture));

    assertThat(HostileMembers.FieldlessSideTableToStringDoor.SIDE_TABLE_READS)
        .as("the structured path must not enter toString() either")
        .hasValue(0);
    assertThat(rendered)
        .as("the type is still named, and no failure marker stands in for it")
        .contains("FieldlessSideTableToStringDoor")
        .doesNotContain(sentinel)
        .doesNotContain("<error");
  }

  /**
   * The {@code number-subclass-tostring-door} row, flat: a user {@code Number} subclass is a
   * composite whose base happens to be numeric, so the scalar-numeric fast path may not read its
   * own text. The field walk is the whole expectation — the type named, the deny-listed field
   * withheld — and it is what the structured path already produced for the same value.
   */
  @Test
  void numberSubclassToStringDoorRowIsWalkedRatherThanReadOnTheFlatPath() {
    var sentinel = "sentinel-number-subclass";
    var fixture = new HostileMembers.NumberSubclassToStringDoor(sentinel);

    var rendered = renderer.render(fixture);

    assertThat(rendered)
        .as("a Number subclass carrying a deny-listed field is walked, never stringified")
        .contains("NumberSubclassToStringDoor")
        .contains("[REDACTED]")
        .doesNotContain(sentinel)
        .doesNotContain("<error");
  }

  /** The same row on the structured path: one decision, so the two channels cannot differ. */
  @Test
  void numberSubclassToStringDoorRowIsWalkedRatherThanReadOnTheStructuredPath() {
    var sentinel = "sentinel-number-subclass-structured";
    var fixture = new HostileMembers.NumberSubclassToStringDoor(sentinel);

    var rendered = String.valueOf(renderer.renderStructured(fixture));

    assertThat(rendered)
        .as("the structured channel walks the same value the same way")
        .contains("NumberSubclassToStringDoor")
        .contains("[REDACTED]")
        .doesNotContain(sentinel)
        .doesNotContain("<error");
  }
}
