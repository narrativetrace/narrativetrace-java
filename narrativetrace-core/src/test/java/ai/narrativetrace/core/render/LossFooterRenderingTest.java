/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceLoss;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.render.NarrativeRenderer;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Shedding must be loud: a capture that lost events says so in every format that has somewhere to
 * say it, and a clean capture says nothing at all.
 *
 * <p>The second half matters as much as the first. A footer that appeared on clean runs would
 * change every approved narrative and every committed fixture, so "silent at zero loss" is asserted
 * for each format alongside "loud when lossy".
 */
class LossFooterRenderingTest {

  private static final TraceLoss SHED = new TraceLoss(1204, 0, 0);
  private static final TraceLoss REFUSED = new TraceLoss(0, 3, 12);
  private static final TraceLoss BOTH = new TraceLoss(7, 1, 4);

  /** Every core renderer with a footer slot, named as the format a reader would ask for. */
  static Stream<org.junit.jupiter.params.provider.Arguments> renderers() {
    return Stream.of(
        org.junit.jupiter.params.provider.Arguments.of(
            "text", (NarrativeRenderer) new IndentedTextRenderer()::render),
        org.junit.jupiter.params.provider.Arguments.of(
            "prose", (NarrativeRenderer) new ProseRenderer()::render),
        org.junit.jupiter.params.provider.Arguments.of(
            "markdown", (NarrativeRenderer) new MarkdownRenderer()::render),
        org.junit.jupiter.params.provider.Arguments.of(
            "markdown-document",
            (NarrativeRenderer)
                tree ->
                    new MarkdownRenderer()
                        .renderDocument(
                            tree, new TraceMetadata("Scenario", ScenarioResult.SUCCESS))));
  }

  @ParameterizedTest(name = "{0} announces shed events")
  @MethodSource("renderers")
  void aShedCaptureSaysSoInEveryFormatWithAFooter(String format, NarrativeRenderer renderer) {
    var rendered = renderer.render(tree(SHED));

    assertThat(rendered).as(format).contains("Incomplete narrative");
    assertThat(rendered).as(format).contains("1204 events shed under load");
    assertThat(rendered).as(format).contains(LossFooter.CAPACITY_KEY);
  }

  @ParameterizedTest(name = "{0} stays silent on a clean capture")
  @MethodSource("renderers")
  void aCleanCaptureSaysNothingInAnyFormat(String format, NarrativeRenderer renderer) {
    var rendered = renderer.render(tree(TraceLoss.none()));

    assertThat(rendered).as(format).doesNotContain("Incomplete narrative");
    assertThat(rendered).as(format).doesNotContain(LossFooter.CAPACITY_KEY);
  }

  @Test
  void theMarkdownFooterIsABlockquoteSoItRendersAsACallout() {
    var rendered = new MarkdownRenderer().render(tree(SHED));

    assertThat(rendered).contains("\n\n> ⚠ Incomplete narrative:");
  }

  @Test
  void refusedScopesAreNamedWithBothCountsAndNoBufferAdvice() {
    var sentence = LossFooter.sentence(tree(REFUSED));

    assertThat(sentence)
        .isEqualTo(
            "⚠ Incomplete narrative: 3 async scopes not adopted "
                + "(adoption cap), 12 spans missing.");
    assertThat(sentence).doesNotContain(LossFooter.CAPACITY_KEY);
  }

  @Test
  void bothLossModesAreReportedInOneSentence() {
    var sentence = LossFooter.sentence(BOTH);

    assertThat(sentence)
        .contains("7 events shed under load")
        .contains("1 async scope not adopted")
        .contains("4 spans missing")
        .contains(LossFooter.CAPACITY_KEY);
  }

  @Test
  void singularsAreSingularAndPluralsArePlural() {
    assertThat(LossFooter.sentence(new TraceLoss(1, 0, 0))).contains("1 event shed");
    assertThat(LossFooter.sentence(new TraceLoss(2, 0, 0))).contains("2 events shed");
    assertThat(LossFooter.sentence(new TraceLoss(0, 1, 1)))
        .contains("1 async scope not adopted")
        .contains("1 span missing");
  }

  @Test
  void noLossAndANullReadingBothProduceNothing() {
    assertThat(LossFooter.sentence(TraceLoss.none())).isEmpty();
    assertThat(LossFooter.sentence((TraceLoss) null)).isEmpty();
    assertThat(LossFooter.block(tree(TraceLoss.none()), "%% ")).isEmpty();
  }

  @Test
  void theBlockCarriesTheCallersCommentSyntaxOnTheFooterLine() {
    assertThat(LossFooter.block(tree(SHED), "%% ")).startsWith("\n\n%% ⚠ Incomplete narrative:");
  }

  @Test
  void frontmatterCarriesTheMachineReadableHalfOnlyWhenSomethingWasLost() {
    var lossy = new FrontmatterBuilder().scenario("Checkout").build(tree(BOTH));
    var clean = new FrontmatterBuilder().scenario("Checkout").build(tree(TraceLoss.none()));

    assertThat(lossy)
        .contains("incomplete: true")
        .contains("dropped_events: 7")
        .contains("refused_scopes: 1")
        .contains("refused_spans: 4");
    assertThat(clean).doesNotContain("incomplete:").doesNotContain("dropped_events:");
  }

  /**
   * The structural artifact is the approval baseline and the conformance fixture format, and its
   * contract is byte-identical output for identical <em>behaviour</em>. A footer that appeared
   * because an unrelated buffer filled would make every baseline non-deterministic.
   */
  @Test
  void theStructuralArtifactNeverCarriesTheFooterBecauseItMustStayDeterministic() {
    var renderer = new StructuralTraceRenderer();

    assertThat(renderer.render(tree(SHED))).isEqualTo(renderer.render(tree(TraceLoss.none())));
    assertThat(renderer.renderDocument(tree(SHED), "Checkout"))
        .isEqualTo(renderer.renderDocument(tree(TraceLoss.none()), "Checkout"));
  }

  @Test
  void anEmptyTreeWithLossStillAnnouncesIt() {
    var empty = new DefaultTraceTree(List.of(), null, SHED);

    assertThat(new IndentedTextRenderer().render(empty)).contains("Incomplete narrative");
  }

  @Test
  void aHandBuiltTreeReportsNoLossWithoutBeingToldAnything() {
    assertThat(new DefaultTraceTree(List.of(node())).loss()).isEqualTo(TraceLoss.none());
    assertThat(new DefaultTraceTree(List.of(node()), null, null).loss())
        .isEqualTo(TraceLoss.none());
  }

  private static TraceTree tree(TraceLoss loss) {
    return new DefaultTraceTree(List.of(node()), null, loss);
  }

  private static TraceNode node() {
    return new TraceNode(
        new MethodSignature("OrderService", "placeOrder", List.of()),
        List.of(),
        new TraceOutcome.Returned("\"ok\""),
        1_000_000L);
  }
}
