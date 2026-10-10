/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.ConcurrencyInfo;
import ai.narrativetrace.api.event.ConcurrencyKind;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.core.render.MarkdownRenderer;
import ai.narrativetrace.core.render.ProseRenderer;
import ai.narrativetrace.core.render.SpanId;
import ai.narrativetrace.core.render.StructuralTraceRenderer;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Phase 7 D8: span ids are DERIVED from tree position, so the {@code .nt} must print exactly one id
 * per node of the tree and each id must lead to the node whose line carries it — a bijection
 * between printed ids and tree positions — and every other flavour must cite the same span by the
 * same id.
 *
 * <p>The oracle here never calls a renderer: it resolves an id by walking the tree path by path,
 * numbering one node's children segment by segment — a fork or async group's members by {@code
 * Class.method}, a fire-and-forget launcher as one position — exactly as the format spec states it.
 * Lives outside the renderers' package on purpose, so it can only see the public contract.
 */
class SpanIdBijectionPropertyTest {

  private static final List<String> CLASSES = List.of("Alpha", "Bravo", "Charlie");
  private static final List<String> METHODS = List.of("run", "check", "send");

  @Property(tries = 300)
  void theStructuralTracePrintsEveryTreePositionExactlyOnce(@ForAll("trees") TraceTree tree) {
    var printed = new ArrayList<String>();
    for (var line : new StructuralTraceRenderer().render(tree).lines().toList()) {
      var id = SpanId.of(line);
      if (id == null) {
        continue;
      }
      printed.add(id);
      var node = resolve(tree, id);
      assertThat(node).as("%s resolves to a node", id).isNotNull();
      assertThat(line)
          .as("the line citing %s describes that node", id)
          .contains(isLauncher(node) ? "~ fire-and-forget" : signature(node) + "(");
    }

    assertThat(new HashSet<>(printed)).as("no id is printed twice").hasSize(printed.size());
    assertThat(printed).as("every node is printed").hasSize(countNodes(tree.roots()));
  }

  @Property(tries = 300)
  void everyFlavourCitesTheSameSpanByTheSameId(@ForAll("trees") TraceTree tree) {
    var structural = idsIn(new StructuralTraceRenderer().render(tree), Placement.LEADING);

    for (var flavour :
        List.of(
            new Flavour(new IndentedTextRenderer().render(tree), Placement.TRAILING),
            new Flavour(MarkdownRenderer.unfolded().render(tree), Placement.TRAILING),
            new Flavour(new ProseRenderer().render(tree), Placement.PARENTHESIZED))) {
      for (var cited : citations(flavour)) {
        assertThat(structural).as("%s exists in the .nt", cited.id()).contains(cited.id());
        var node = resolve(tree, cited.id());
        if (!isLauncher(node)) {
          assertThat(cited.line()).as("%s cites its own span", cited.id()).contains(words(node));
        }
      }
    }
  }

  private enum Placement {
    LEADING,
    TRAILING,
    PARENTHESIZED
  }

  private record Flavour(String text, Placement placement) {}

  private record Citation(String id, String line) {}

  private static List<String> idsIn(String text, Placement placement) {
    return citations(new Flavour(text, placement)).stream().map(Citation::id).toList();
  }

  private static List<Citation> citations(Flavour flavour) {
    var found = new ArrayList<Citation>();
    for (var line : flavour.text().lines().toList()) {
      var id = citedId(line, flavour.placement());
      if (id != null) {
        found.add(new Citation(id, line));
      }
    }
    return found;
  }

  private static String citedId(String line, Placement placement) {
    return switch (placement) {
      case LEADING -> SpanId.of(line);
      case TRAILING -> wellFormedOrNull(line.substring(line.lastIndexOf(' ') + 1));
      case PARENTHESIZED -> {
        var open = line.indexOf("(#");
        var close = open < 0 ? -1 : line.indexOf(')', open);
        yield close < 0 ? null : wellFormedOrNull(line.substring(open + 1, close));
      }
    };
  }

  private static String wellFormedOrNull(String token) {
    return SpanId.isWellFormed(token) ? token : null;
  }

  /** How the line names a node: Markdown bolds and prose splits words, so match loosely. */
  private static String words(TraceNode node) {
    return node.signature().methodName();
  }

  private static String signature(TraceNode node) {
    return node.signature().className() + "." + node.signature().methodName();
  }

  private static boolean isLauncher(TraceNode node) {
    return node.concurrency() != null
        && node.concurrency().kind() == ConcurrencyKind.FIRE_AND_FORGET;
  }

  // --- the oracle -------------------------------------------------------------------------------

  private static TraceNode resolve(TraceTree tree, String id) {
    var siblings = tree.roots();
    TraceNode node = null;
    for (var step : id.substring(1).split("\\.")) {
      var ordered = canonicalOrder(siblings);
      var position = Integer.parseInt(step);
      if (position < 1 || position > ordered.size()) {
        return null;
      }
      node = ordered.get(position - 1);
      siblings = node.children();
    }
    return node;
  }

  /** One node per position: consecutive members of one group sorted by Class.method, stably. */
  private static List<TraceNode> canonicalOrder(List<TraceNode> siblings) {
    var ordered = new ArrayList<TraceNode>();
    var i = 0;
    while (i < siblings.size()) {
      var group = groupOf(siblings.get(i));
      var end = i + 1;
      while (group != null && end < siblings.size() && group.equals(groupOf(siblings.get(end)))) {
        end++;
      }
      var segment = new ArrayList<>(siblings.subList(i, end));
      if (group != null && !isLauncher(segment.get(0))) {
        segment.sort(Comparator.comparing(SpanIdBijectionPropertyTest::signature));
      }
      ordered.addAll(segment);
      i = end;
    }
    return ordered;
  }

  private static String groupOf(TraceNode node) {
    return node.concurrency() == null ? null : node.concurrency().groupId();
  }

  private static int countNodes(List<TraceNode> nodes) {
    var count = 0;
    for (var node : nodes) {
      count += 1 + countNodes(node.children());
    }
    return count;
  }

  // --- generators -------------------------------------------------------------------------------

  @Provide
  Arbitrary<TraceTree> trees() {
    return Arbitraries.longs()
        .between(0, Long.MAX_VALUE)
        .map(
            seed ->
                new DefaultTraceTree(siblings(new java.util.Random(seed), 0, new AtomicInteger())));
  }

  /**
   * A sibling list mixing plain calls, fork groups (members with clashing signatures, so the stable
   * sort matters) and fire-and-forget launchers, each launcher with its own group id.
   */
  private static List<TraceNode> siblings(
      java.util.Random random, int depth, AtomicInteger groups) {
    var result = new ArrayList<TraceNode>();
    var count = depth > 3 ? 0 : random.nextInt(4);
    for (var i = 0; i < count; i++) {
      switch (random.nextInt(4)) {
        case 0 -> result.addAll(forkGroup(random, depth, groups));
        case 1 -> result.add(launcher(random, depth, groups));
        default -> result.add(node(random, depth, null, groups));
      }
    }
    return result;
  }

  private static List<TraceNode> forkGroup(
      java.util.Random random, int depth, AtomicInteger groups) {
    var info =
        new ConcurrencyInfo(
            "fork-" + groups.incrementAndGet(), "pool", 1, false, ConcurrencyKind.FORK_JOIN);
    var members = new ArrayList<TraceNode>();
    for (var i = 0; i < 1 + random.nextInt(3); i++) {
      members.add(node(random, depth, info, groups));
    }
    return members;
  }

  private static TraceNode launcher(java.util.Random random, int depth, AtomicInteger groups) {
    var info =
        new ConcurrencyInfo(
            "faf-" + groups.incrementAndGet(), "bg", 1, false, ConcurrencyKind.FIRE_AND_FORGET);
    return new TraceNode(
        new MethodSignature("Parent", "launch", List.of()),
        siblings(random, depth + 1, groups),
        null,
        0L,
        0L,
        info);
  }

  private static TraceNode node(
      java.util.Random random, int depth, ConcurrencyInfo info, AtomicInteger groups) {
    return new TraceNode(
        new MethodSignature(
            CLASSES.get(random.nextInt(CLASSES.size())),
            METHODS.get(random.nextInt(METHODS.size())),
            List.of()),
        siblings(random, depth + 1, groups),
        new TraceOutcome.Returned(null),
        0L,
        0L,
        info);
  }
}
