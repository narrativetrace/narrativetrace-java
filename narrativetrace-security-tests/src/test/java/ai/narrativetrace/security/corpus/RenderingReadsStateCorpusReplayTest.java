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
 * <p><b>@llmNote</b> Two further rows close the gaps the abstract-base refinement left open: {@code
 * structured-path-user-collection-not-enumerated} and {@code
 * fieldless-abstract-subclass-tostring-door} pin the two ways the rule used to be escaped — the
 * STRUCTURED path enumerating any {@code Collection}/{@code Map} unconditionally (origin-blind),
 * and a fieldless abstract-base subclass reaching its override through the inherited {@code
 * toString()} {@code rendersItsOwnString} still trusted. Both are live regression guards now that
 * both are closed.
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
   * Gap 2 — a FIELDLESS {@code AbstractCollection} subclass is trusted by {@code
   * rendersItsOwnString} to stand behind its own text because it declares no field, but the text it
   * is trusted for is the inherited {@code AbstractCollection.toString()}, which walks {@code
   * iterator()} internally. The flat path reaches this door too — bullets 11/12 in {@code
   * RenderingReadsStateNeverRunsBehaviourTest} only ever pinned the field-bearing sibling, which
   * {@code rendersItsOwnString} already distrusts for an unrelated reason.
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
}
