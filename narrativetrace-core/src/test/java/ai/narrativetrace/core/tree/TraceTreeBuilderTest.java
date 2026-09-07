/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.api.event.ConcurrencyInfo;
import ai.narrativetrace.api.event.ConcurrencyKind;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TestSpanContext;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TraceTreeBuilderTest {

  private static final MethodSignature SIG_A =
      new MethodSignature("ServiceA", "methodA", List.of());
  private static final MethodSignature SIG_B =
      new MethodSignature("ServiceB", "methodB", List.of());
  private static final MethodSignature SIG_C =
      new MethodSignature("ServiceC", "methodC", List.of());

  @Test
  void singleEnterExitPairBuildsOneRootNode() {
    var sc0 = TestSpanContext.create();
    var events =
        List.<TraceEvent>of(
            new TraceEvent.EnterEvent(sc0, 1000L, SIG_A),
            new TraceEvent.ExitEvent(sc0, 2000L, new TraceOutcome.Returned("\"ok\""), null));

    var tree = TraceTreeBuilder.build(events, TracingLevel.DETAIL);

    assertThat(tree.roots()).hasSize(1);
    var root = tree.roots().get(0);
    assertThat(root.signature()).isEqualTo(SIG_A);
    assertThat(root.outcome()).isEqualTo(new TraceOutcome.Returned("\"ok\""));
    assertThat(root.children()).isEmpty();
    assertThat(root.durationNanos()).isEqualTo(1000L);
    assertThat(root.startTimeNanos()).isEqualTo(1000L);
  }

  @Test
  void nestedEnterExitPairsBuildParentChildTree() {
    var sc0 = TestSpanContext.create();
    var sc1 = TestSpanContext.childOf(sc0);
    var events =
        List.<TraceEvent>of(
            new TraceEvent.EnterEvent(sc0, 1000L, SIG_A),
            new TraceEvent.EnterEvent(sc1, 1100L, SIG_B),
            new TraceEvent.ExitEvent(sc1, 1500L, new TraceOutcome.Returned("true"), null),
            new TraceEvent.ExitEvent(sc0, 2000L, new TraceOutcome.Returned("\"ok\""), null));

    var tree = TraceTreeBuilder.build(events, TracingLevel.DETAIL);

    assertThat(tree.roots()).hasSize(1);
    var root = tree.roots().get(0);
    assertThat(root.children()).hasSize(1);
    assertThat(root.children().get(0).signature()).isEqualTo(SIG_B);
    assertThat(root.children().get(0).durationNanos()).isEqualTo(400L);
  }

  @Test
  void siblingEnterExitPairsBuildMultipleRoots() {
    var sc0 = TestSpanContext.create();
    var sc1 = TestSpanContext.create();
    var events =
        List.<TraceEvent>of(
            new TraceEvent.EnterEvent(sc0, 1000L, SIG_A),
            new TraceEvent.ExitEvent(sc0, 2000L, new TraceOutcome.Returned(null), null),
            new TraceEvent.EnterEvent(sc1, 3000L, SIG_B),
            new TraceEvent.ExitEvent(sc1, 4000L, new TraceOutcome.Returned(null), null));

    var tree = TraceTreeBuilder.build(events, TracingLevel.DETAIL);

    assertThat(tree.roots()).hasSize(2);
    assertThat(tree.roots().get(0).signature()).isEqualTo(SIG_A);
    assertThat(tree.roots().get(1).signature()).isEqualTo(SIG_B);
  }

  @Test
  void enterWithoutMatchingExitProducesIncompleteNode() {
    var sc0 = TestSpanContext.create();
    var events = List.<TraceEvent>of(new TraceEvent.EnterEvent(sc0, 1000L, SIG_A));

    var tree = TraceTreeBuilder.build(events, TracingLevel.DETAIL);

    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).outcome()).isInstanceOf(TraceOutcome.Incomplete.class);
    assertThat(tree.roots().get(0).durationNanos()).isZero();
  }

  @Test
  void exitWithoutMatchingEnterIsIgnored() {
    var sc99 = TestSpanContext.create();
    var events =
        List.<TraceEvent>of(
            new TraceEvent.ExitEvent(sc99, 2000L, new TraceOutcome.Returned(null), null));

    var tree = TraceTreeBuilder.build(events, TracingLevel.DETAIL);

    assertThat(tree.roots()).isEmpty();
  }

  @Test
  void errorsLevelDiscardsSuccessfulRootsKeepsThrewAndIncomplete() {
    var sc0 = TestSpanContext.create();
    var sc1 = TestSpanContext.create();
    var sc2 = TestSpanContext.create();
    var events =
        List.<TraceEvent>of(
            new TraceEvent.EnterEvent(sc0, 1000L, SIG_A),
            new TraceEvent.ExitEvent(sc0, 2000L, new TraceOutcome.Returned("\"ok\""), null),
            new TraceEvent.EnterEvent(sc1, 3000L, SIG_B),
            new TraceEvent.ExitEvent(
                sc1, 4000L, new TraceOutcome.Threw(new RuntimeException("boom")), null),
            new TraceEvent.EnterEvent(sc2, 5000L, SIG_C));

    var tree = TraceTreeBuilder.build(events, TracingLevel.ERRORS);

    assertThat(tree.roots()).hasSize(2);
    assertThat(tree.roots().get(0).outcome()).isInstanceOf(TraceOutcome.Threw.class);
    assertThat(tree.roots().get(1).outcome()).isInstanceOf(TraceOutcome.Incomplete.class);
  }

  @Test
  void detailLevelKeepsAllNodes() {
    var sc0 = TestSpanContext.create();
    var events =
        List.<TraceEvent>of(
            new TraceEvent.EnterEvent(sc0, 1000L, SIG_A),
            new TraceEvent.ExitEvent(sc0, 2000L, new TraceOutcome.Returned("\"ok\""), null));

    var tree = TraceTreeBuilder.build(events, TracingLevel.DETAIL);

    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).outcome()).isInstanceOf(TraceOutcome.Returned.class);
  }

  @Test
  void deepNestingBuildsCorrectMultiLevelTree() {
    var sc0 = TestSpanContext.create();
    var sc1 = TestSpanContext.childOf(sc0);
    var sc2 = TestSpanContext.childOf(sc1);
    var events =
        List.<TraceEvent>of(
            new TraceEvent.EnterEvent(sc0, 1000L, SIG_A),
            new TraceEvent.EnterEvent(sc1, 1100L, SIG_B),
            new TraceEvent.EnterEvent(sc2, 1200L, SIG_C),
            new TraceEvent.ExitEvent(sc2, 1300L, new TraceOutcome.Returned("3"), null),
            new TraceEvent.ExitEvent(sc1, 1400L, new TraceOutcome.Returned("2"), null),
            new TraceEvent.ExitEvent(sc0, 1500L, new TraceOutcome.Returned("1"), null));

    var tree = TraceTreeBuilder.build(events, TracingLevel.DETAIL);

    assertThat(tree.roots()).hasSize(1);
    var level0 = tree.roots().get(0);
    assertThat(level0.children()).hasSize(1);
    var level1 = level0.children().get(0);
    assertThat(level1.children()).hasSize(1);
    var level2 = level1.children().get(0);
    assertThat(level2.children()).isEmpty();
    assertThat(level2.signature()).isEqualTo(SIG_C);
  }

  @Test
  void durationCalculatedFromEnterExitTimestamps() {
    var sc0 = TestSpanContext.create();
    var events =
        List.<TraceEvent>of(
            new TraceEvent.EnterEvent(sc0, 500L, SIG_A),
            new TraceEvent.ExitEvent(sc0, 1700L, new TraceOutcome.Returned(null), null));

    var tree = TraceTreeBuilder.build(events, TracingLevel.DETAIL);

    assertThat(tree.roots().get(0).durationNanos()).isEqualTo(1200L);
  }

  @Test
  void errorContextFromExitEventOverridesSignature() {
    var sc0 = TestSpanContext.create();
    var sig = new MethodSignature("Svc", "op", List.of(), null, null);
    var events =
        List.<TraceEvent>of(
            new TraceEvent.EnterEvent(sc0, 1000L, sig),
            new TraceEvent.ExitEvent(
                sc0, 2000L, new TraceOutcome.Threw(new RuntimeException("fail")), "card expired"));

    var tree = TraceTreeBuilder.build(events, TracingLevel.DETAIL);

    assertThat(tree.roots().get(0).signature().errorContext()).isEqualTo("card expired");
  }

  @Test
  void errorContextRebuildPreservesNarrationTemplateAndPackage() {
    var sc0 = TestSpanContext.create();
    var sig =
        new MethodSignature(
            "Svc", "op", List.of(), "Charging C-1", null, "Charging {customerId}", "com.acme");
    var events =
        List.<TraceEvent>of(
            new TraceEvent.EnterEvent(sc0, 1000L, sig),
            new TraceEvent.ExitEvent(
                sc0, 2000L, new TraceOutcome.Threw(new RuntimeException("fail")), "card expired"));

    var tree = TraceTreeBuilder.build(events, TracingLevel.DETAIL);

    var rebuilt = tree.roots().get(0).signature();
    assertThat(rebuilt.errorContext()).isEqualTo("card expired");
    assertThat(rebuilt.narrationTemplate()).isEqualTo("Charging {customerId}");
    assertThat(rebuilt.packageName()).isEqualTo("com.acme");
    assertThat(rebuilt.narration()).isEqualTo("Charging C-1");
  }

  @Test
  void errorsLevelPreservesParentOfNestedError() {
    var sc0 = TestSpanContext.create();
    var sc1 = TestSpanContext.childOf(sc0);
    var events =
        List.<TraceEvent>of(
            new TraceEvent.EnterEvent(sc0, 1000L, SIG_A),
            new TraceEvent.EnterEvent(sc1, 1100L, SIG_B),
            new TraceEvent.ExitEvent(
                sc1, 1200L, new TraceOutcome.Threw(new RuntimeException("boom")), null),
            new TraceEvent.ExitEvent(sc0, 1300L, new TraceOutcome.Returned("\"ok\""), null));

    var tree = TraceTreeBuilder.build(events, TracingLevel.ERRORS);

    assertThat(tree.roots()).hasSize(1);
    var root = tree.roots().get(0);
    assertThat(root.signature().methodName()).isEqualTo("methodA");
    assertThat(root.children()).hasSize(1);
    assertThat(root.children().get(0).signature().methodName()).isEqualTo("methodB");
    assertThat(root.children().get(0).outcome()).isInstanceOf(TraceOutcome.Threw.class);
  }

  @Test
  void errorsLevelPreservesFullChainForDeeplyNestedError() {
    var sc0 = TestSpanContext.create();
    var sc1 = TestSpanContext.childOf(sc0);
    var sc2 = TestSpanContext.childOf(sc1);
    var events =
        List.<TraceEvent>of(
            new TraceEvent.EnterEvent(sc0, 1000L, SIG_A),
            new TraceEvent.EnterEvent(sc1, 1100L, SIG_B),
            new TraceEvent.EnterEvent(sc2, 1200L, SIG_C),
            new TraceEvent.ExitEvent(
                sc2, 1300L, new TraceOutcome.Threw(new RuntimeException("deep")), null),
            new TraceEvent.ExitEvent(sc1, 1400L, new TraceOutcome.Returned("\"ok\""), null),
            new TraceEvent.ExitEvent(sc0, 1500L, new TraceOutcome.Returned("\"ok\""), null));

    var tree = TraceTreeBuilder.build(events, TracingLevel.ERRORS);

    assertThat(tree.roots()).hasSize(1);
    var a = tree.roots().get(0);
    assertThat(a.signature().methodName()).isEqualTo("methodA");
    assertThat(a.children()).hasSize(1);
    var b = a.children().get(0);
    assertThat(b.signature().methodName()).isEqualTo("methodB");
    assertThat(b.children()).hasSize(1);
    var c = b.children().get(0);
    assertThat(c.signature().methodName()).isEqualTo("methodC");
    assertThat(c.outcome()).isInstanceOf(TraceOutcome.Threw.class);
  }

  @Test
  void errorsLevelPrunesSuccessfulSiblingOfErrorChild() {
    var sc0 = TestSpanContext.create();
    var sc1 = TestSpanContext.childOf(sc0);
    var sc2 = TestSpanContext.childOf(sc0);
    var sc3 = TestSpanContext.childOf(sc0);
    var sigD = new MethodSignature("ServiceD", "methodD", List.of());
    var events =
        List.<TraceEvent>of(
            new TraceEvent.EnterEvent(sc0, 1000L, SIG_A),
            new TraceEvent.EnterEvent(sc1, 1100L, SIG_B),
            new TraceEvent.ExitEvent(
                sc1, 1200L, new TraceOutcome.Threw(new RuntimeException("fail")), null),
            new TraceEvent.EnterEvent(sc2, 1300L, SIG_C),
            new TraceEvent.ExitEvent(sc2, 1400L, new TraceOutcome.Returned("\"ok\""), null),
            new TraceEvent.EnterEvent(sc3, 1500L, sigD),
            new TraceEvent.ExitEvent(sc0, 1700L, new TraceOutcome.Returned("\"ok\""), null));

    var tree = TraceTreeBuilder.build(events, TracingLevel.ERRORS);

    assertThat(tree.roots()).hasSize(1);
    var root = tree.roots().get(0);
    assertThat(root.signature().methodName()).isEqualTo("methodA");
    // Only the error child (B) and incomplete child (D) retained; successful C pruned
    assertThat(root.children()).hasSize(2);
    assertThat(root.children().get(0).signature().methodName()).isEqualTo("methodB");
    assertThat(root.children().get(1).signature().methodName()).isEqualTo("methodD");
  }

  @Test
  void errorsLevelKeepsRootLevelErrorAsRoot() {
    var sc0 = TestSpanContext.create();
    var events =
        List.<TraceEvent>of(
            new TraceEvent.EnterEvent(sc0, 1000L, SIG_A),
            new TraceEvent.ExitEvent(
                sc0, 2000L, new TraceOutcome.Threw(new RuntimeException("root fail")), null));

    var tree = TraceTreeBuilder.build(events, TracingLevel.ERRORS);

    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature().methodName()).isEqualTo("methodA");
    assertThat(tree.roots().get(0).outcome()).isInstanceOf(TraceOutcome.Threw.class);
  }

  @Test
  void errorsLevelPreservesMultipleIndependentErrorPaths() {
    var sc0 = TestSpanContext.create();
    var sc1 = TestSpanContext.childOf(sc0);
    var sc2 = TestSpanContext.create();
    var events =
        List.<TraceEvent>of(
            new TraceEvent.EnterEvent(sc0, 1000L, SIG_A),
            new TraceEvent.EnterEvent(sc1, 1100L, SIG_B),
            new TraceEvent.ExitEvent(
                sc1, 1200L, new TraceOutcome.Threw(new RuntimeException("fail1")), null),
            new TraceEvent.ExitEvent(sc0, 1300L, new TraceOutcome.Returned("\"ok\""), null),
            new TraceEvent.EnterEvent(sc2, 2000L, SIG_C),
            new TraceEvent.ExitEvent(
                sc2, 2100L, new TraceOutcome.Threw(new RuntimeException("fail2")), null));

    var tree = TraceTreeBuilder.build(events, TracingLevel.ERRORS);

    assertThat(tree.roots()).hasSize(2);
    assertThat(tree.roots().get(0).signature().methodName()).isEqualTo("methodA");
    assertThat(tree.roots().get(0).children()).hasSize(1);
    assertThat(tree.roots().get(0).children().get(0).signature().methodName()).isEqualTo("methodB");
    assertThat(tree.roots().get(1).signature().methodName()).isEqualTo("methodC");
    assertThat(tree.roots().get(1).children()).isEmpty();
  }

  @Test
  void treeBuilderPropagatesConcurrencyFromEnterEvent() {
    var sc0 = TestSpanContext.create();
    var info = new ConcurrencyInfo("g1", "pool-1", 42L, false, ConcurrencyKind.FORK_JOIN);
    var events =
        List.<TraceEvent>of(
            new TraceEvent.EnterEvent(sc0, 1000L, SIG_A, info),
            new TraceEvent.ExitEvent(sc0, 2000L, new TraceOutcome.Returned("\"ok\""), null));

    var tree = TraceTreeBuilder.build(events, TracingLevel.DETAIL);

    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).concurrency()).isEqualTo(info);
  }

  @Test
  void summaryLevelPreservesNonLeafExceptionChainNodes() {
    var root = TestSpanContext.create();
    var middle = TestSpanContext.childOf(root);
    var leaf = TestSpanContext.childOf(middle);
    var events =
        List.<TraceEvent>of(
            new TraceEvent.EnterEvent(
                root, 1000L, new MethodSignature("OrderService", "placeOrder", List.of())),
            new TraceEvent.EnterEvent(
                middle, 1100L, new MethodSignature("PaymentService", "charge", List.of())),
            new TraceEvent.EnterEvent(
                leaf, 1200L, new MethodSignature("GatewayClient", "authorize", List.of())),
            new TraceEvent.ExitEvent(leaf, 1300L, new TraceOutcome.Returned("\"ok\""), null),
            new TraceEvent.ExitEvent(
                middle, 1400L, new TraceOutcome.Threw(new RuntimeException("declined")), null),
            new TraceEvent.ExitEvent(root, 1500L, new TraceOutcome.Returned("\"fallback\""), null));

    var tree = TraceTreeBuilder.build(events, TracingLevel.SUMMARY);

    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).children()).hasSize(1);
    assertThat(tree.roots().get(0).children().get(0).signature().className())
        .isEqualTo("PaymentService");
    assertThat(tree.roots().get(0).children().get(0).outcome())
        .isInstanceOf(TraceOutcome.Threw.class);
  }

  @Test
  void offBuildsAnEmptyTreeEvenWhenThereAreEventsToBuildFrom() {
    var sc0 = TestSpanContext.create();
    var events =
        List.<TraceEvent>of(
            new TraceEvent.EnterEvent(sc0, 1000L, SIG_A),
            new TraceEvent.ExitEvent(sc0, 2000L, new TraceOutcome.Returned("\"ok\""), null));

    var tree = TraceTreeBuilder.build(events, TracingLevel.OFF);

    assertThat(tree.roots()).isEmpty();
    assertThat(tree.isEmpty()).isTrue();
    assertThat(tree.traceId()).isNull();
  }

  @Test
  void emptyEventListProducesEmptyTree() {
    var tree = TraceTreeBuilder.build(List.of(), TracingLevel.DETAIL);

    assertThat(tree.roots()).isEmpty();
    assertThat(tree.isEmpty()).isTrue();
  }

  // --- Cross-runtime residual recursion: construction itself must be bounded and cycle-safe,
  // the
  // same way every renderer's own walk already is (TreeWalk). buildNodeRecursive runs before any
  // renderer sees the tree, for every tracing level except OFF, so a crash here happens earlier
  // and more broadly than a renderer crash ever could. ---

  @Test
  void veryDeepLegitimateChainBuildsFullyWithoutStackOverflow() {
    var depth = 5_000;
    var events = chainEvents(depth);

    var tree = TraceTreeBuilder.build(events, TracingLevel.DETAIL);

    var node = tree.roots().get(0);
    for (var i = 0; i < depth; i++) {
      assertThat(node.children()).hasSize(1);
      node = node.children().get(0);
    }
    assertThat(node.children()).isEmpty();
    assertThat(node.signature().methodName()).isEqualTo("leaf");
  }

  @Test
  void chainDeeperThanMaxDepthIsTruncatedInsteadOfOverflowing() {
    var depth = TreeWalk.MAX_DEPTH + 50;
    var events = chainEvents(depth);

    assertThatCode(() -> TraceTreeBuilder.build(events, TracingLevel.DETAIL))
        .doesNotThrowAnyException();

    var tree = TraceTreeBuilder.build(events, TracingLevel.DETAIL);
    var node = tree.roots().get(0);
    for (var i = 0; i < TreeWalk.MAX_DEPTH; i++) {
      assertThat(node.children()).hasSize(1);
      node = node.children().get(0);
    }
    // node is now the node at depth MAX_DEPTH: still visited normally, still has the one child the
    // walk stopped at — but that child still contributes itself with no further descent.
    assertThat(node.children()).hasSize(1);
    assertThat(node.children().get(0).children()).isEmpty();
  }

  @Test
  void cyclicSpanParentageTerminatesInsteadOfRecursingForever() {
    // A hostile or malformed event stream can carry two EnterEvents for the same spanId with
    // different parents: the second (last write wins in the enters index) records X's parent as
    // Y, while an earlier occurrence already made X a child of the real root, and Y's own parent
    // is X. Neither X nor Y ever becomes a root (each has a present parent), yet root -> X -> Y ->
    // X is reachable. Structurally impossible from a genuine flat capture, but nothing before this
    // fix stopped a replayed/deserialized/malformed one from producing it.
    var traceId = SpanIdGenerator.traceId();
    var rootId = SpanId.of("0000000000000000");
    var xId = SpanId.of("1111111111111111");
    var yId = SpanId.of("2222222222222222");
    var rootSc = SpanContext.builder(traceId, rootId).build();
    var xViaRoot = SpanContext.builder(traceId, xId).parentSpanId(rootId).build();
    var yViaX = SpanContext.builder(traceId, yId).parentSpanId(xId).build();
    var xViaY = SpanContext.builder(traceId, xId).parentSpanId(yId).build();
    var events =
        List.<TraceEvent>of(
            new TraceEvent.EnterEvent(rootSc, 1000L, SIG_A),
            new TraceEvent.EnterEvent(xViaRoot, 1100L, SIG_B),
            new TraceEvent.EnterEvent(yViaX, 1200L, SIG_C),
            new TraceEvent.EnterEvent(xViaY, 1300L, SIG_B));

    assertThatCode(() -> TraceTreeBuilder.build(events, TracingLevel.DETAIL))
        .doesNotThrowAnyException();

    var tree = TraceTreeBuilder.build(events, TracingLevel.DETAIL);
    assertThat(tree.roots()).hasSize(1);
    var root = tree.roots().get(0);
    assertThat(root.children()).hasSize(1);
    var x = root.children().get(0);
    assertThat(x.children()).hasSize(1);
    var y = x.children().get(0);
    assertThat(y.children()).hasSize(1);
    // The repeated occurrence of X is cut off here: it still contributes itself, but the walk
    // never re-descends into its children a second time.
    assertThat(y.children().get(0).children()).isEmpty();
  }

  @Test
  void errorsLevelSurvivesAVeryDeepErrorChainWithoutStackOverflow() {
    // retainErrorPathsNode runs on whatever buildNodeRecursive already produced: a legitimately
    // deep chain is ordinary, and this must not overflow the stack just because MAX_DEPTH (10 000)
    // is far beyond what plain call-stack recursion survives.
    var depth = 5_000;
    var events = chainEvents(depth, new TraceOutcome.Threw(new RuntimeException("deep failure")));

    var tree = TraceTreeBuilder.build(events, TracingLevel.ERRORS);

    var node = tree.roots().get(0);
    for (var i = 0; i < depth; i++) {
      assertThat(node.children()).hasSize(1);
      node = node.children().get(0);
    }
    assertThat(node.outcome()).isInstanceOf(TraceOutcome.Threw.class);
  }

  @Test
  void summaryLevelSurvivesAVeryDeepChainWithoutStackOverflow() {
    var depth = 5_000;
    var events = chainEvents(depth);

    var tree = TraceTreeBuilder.build(events, TracingLevel.SUMMARY);

    // Every intermediate level is lifted away; only the root and the deepest leaf remain.
    assertThat(tree.roots()).hasSize(1);
    var root = tree.roots().get(0);
    assertThat(root.children()).hasSize(1);
    var leaf = root.children().get(0);
    assertThat(leaf.children()).isEmpty();
    assertThat(leaf.signature().methodName()).isEqualTo("leaf");
  }

  @Test
  void pruneSummaryTerminatesOnAHandBuiltCyclicTree() {
    // pruneSummary/pruneSummaryCollect is package-private specifically so its sharing contract can
    // be unit-tested directly (see its own Javadoc); this hand-builds the cyclic TraceNode shape
    // buildNodeRecursive can no longer itself produce, exercising the walker's own cycle guard
    // rather than the construction step's — defense in depth for a tree that arrived here some
    // other way (a future non-event entry point, a test double).
    var ownChildren = new ArrayList<TraceNode>();
    var inner =
        new TraceNode(
            new MethodSignature("Svc", "inner", List.of()),
            ownChildren,
            new TraceOutcome.Returned("null"));
    ownChildren.add(inner); // self-holding cycle
    var root =
        new TraceNode(
            new MethodSignature("Svc", "outer", List.of()),
            List.of(inner),
            new TraceOutcome.Returned("null"));

    assertThatCode(() -> TraceTreeBuilder.pruneSummary(List.of(root))).doesNotThrowAnyException();
  }

  private static List<TraceEvent> chainEvents(int depth) {
    return chainEvents(depth, new TraceOutcome.Returned("\"ok\""));
  }

  /** A linear chain {@code depth} calls deep; the deepest ("leaf") gets {@code leafOutcome}. */
  private static List<TraceEvent> chainEvents(int depth, TraceOutcome leafOutcome) {
    var events = new ArrayList<TraceEvent>();
    var contexts = new ArrayList<SpanContext>();
    var current = TestSpanContext.create();
    contexts.add(current);
    events.add(new TraceEvent.EnterEvent(current, 0L, SIG_A));
    for (var i = 1; i <= depth; i++) {
      current = TestSpanContext.childOf(current);
      contexts.add(current);
      var sig = i == depth ? new MethodSignature("Leaf", "leaf", List.of()) : SIG_B;
      events.add(new TraceEvent.EnterEvent(current, i, sig));
    }
    for (var i = contexts.size() - 1; i >= 0; i--) {
      var outcome = i == contexts.size() - 1 ? leafOutcome : new TraceOutcome.Returned("null");
      events.add(new TraceEvent.ExitEvent(contexts.get(i), depth + 1L + i, outcome, null));
    }
    return events;
  }
}
