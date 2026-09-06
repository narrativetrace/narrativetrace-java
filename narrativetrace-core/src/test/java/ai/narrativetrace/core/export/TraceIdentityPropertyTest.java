/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.export;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The identity rules as invariants rather than examples: uniqueness of what is generated,
 * inheritance of what is not, and a single identity per tree whatever the tree looks like.
 *
 * <p>Pins the bug class, not the bug: a shared constant, a per-node regeneration and a per-exporter
 * regeneration all break at least one of these properties.
 */
class TraceIdentityPropertyTest {

  @Property
  void twoIndependentSpanLessCapturesNeverShareATraceId(
      @ForAll @AlphaChars @StringLength(min = 1, max = 20) String className,
      @ForAll @AlphaChars @StringLength(min = 1, max = 20) String methodName) {
    var first = TraceIdentity.of(spanLessTree(className, methodName));
    var second = TraceIdentity.of(spanLessTree(className, methodName));

    assertThat(first.traceId()).isNotEqualTo(second.traceId());
    // What describes rather than identifies stays derived, so identical behaviour still groups.
    assertThat(first.storyId()).isEqualTo(second.storyId());
    assertThat(first.chapterId()).isEqualTo(second.chapterId());
  }

  @Property
  void everyResolvedTraceIdIsAValidW3cIdAndNeverAllZeroes(
      @ForAll @AlphaChars @StringLength(min = 1, max = 20) String className,
      @ForAll @AlphaChars @StringLength(min = 1, max = 20) String methodName) {
    var identity = TraceIdentity.of(spanLessTree(className, methodName));

    assertThat(identity.traceId().value()).matches("^[0-9a-f]{32}$");
    assertThat(identity.traceId().value()).isNotEqualTo("0".repeat(32));
    assertThat(identity.traceName()).matches("^[a-z]+ [a-z]+ [a-z]+$");
  }

  @Property
  void resolvingTheSameTreeTwiceYieldsTheSameIdentity(
      @ForAll @AlphaChars @StringLength(min = 1, max = 20) String className,
      @ForAll @AlphaChars @StringLength(min = 1, max = 20) String methodName) {
    // Two exporters read one tree; they must not name two traces.
    var tree = spanLessTree(className, methodName);

    assertThat(TraceIdentity.of(tree)).isEqualTo(TraceIdentity.of(tree));
  }

  @Property
  void aRealSpanContextIsAlwaysInheritedRatherThanRegenerated(
      @ForAll("hexTraceIds") String hex,
      @ForAll @AlphaChars @StringLength(min = 1, max = 20) String className) {
    var sc =
        SpanContext.builder(TraceId.of(hex), SpanId.of("b7ad6b7169203331"))
            .storyId("Real.story")
            .build();
    var contextFreeChild = node(className, "child", null);
    var root = node(className, "root", sc, contextFreeChild);

    var identity = TraceIdentity.of(new DefaultTraceTree(List.of(root)));

    assertThat(identity.traceId().value()).isEqualTo(hex);
    assertThat(identity.storyId()).isEqualTo("Real.story");
    assertThat(identity.chapterId()).isEqualTo("Real.story");
  }

  @Property
  void everyEntryOfATreeCarriesTheOneTraceIdOfThatTree(
      @ForAll @AlphaChars @StringLength(min = 1, max = 20) String className,
      @ForAll @AlphaChars @StringLength(min = 1, max = 20) String methodName) {
    var tree = spanLessTree(className, methodName);

    var entries = TraceTreeCanonicalMapper.fromTree(tree);

    assertThat(entries).isNotEmpty();
    assertThat(entries).extracting(CanonicalEntry::traceId).containsOnly(tree.traceId().toString());
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aVeryDeepTreeResolvesIdentityWithoutStackOverflow() {
    TraceNode current = node("Recursive", "call5000", null);
    for (var i = 0; i < 5_000; i++) {
      current = node("Recursive", "call" + i, null, current);
    }
    var tree = new DefaultTraceTree(List.of(current));

    var identity = TraceIdentity.of(tree);

    assertThat(identity.traceId()).isNotNull();
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void aCyclicTreeResolvesIdentityWithoutHanging() {
    var childHolder = new ArrayList<TraceNode>();
    var b = mutableNode("Recursive", "b", childHolder);
    var a = mutableNode("Recursive", "a", List.of(b));
    childHolder.add(a);
    var tree = new DefaultTraceTree(List.of(a));

    var identity = TraceIdentity.of(tree);

    assertThat(identity.traceId()).isNotNull();
  }

  private static TraceNode mutableNode(
      String className, String methodName, List<TraceNode> children) {
    return new TraceNode(
        new MethodSignature(className, methodName, List.of()),
        children,
        new TraceOutcome.Returned("\"ok\""),
        1_000_000L,
        1_000L,
        null,
        null);
  }

  @Provide
  Arbitrary<String> hexTraceIds() {
    return Arbitraries.strings().withChars("0123456789abcdef".toCharArray()).ofLength(32);
  }

  private static DefaultTraceTree spanLessTree(String className, String methodName) {
    var child = node(className, methodName + "Inner", null);
    return new DefaultTraceTree(List.of(node(className, methodName, null, child)));
  }

  private static TraceNode node(
      String className, String methodName, SpanContext sc, TraceNode... children) {
    return new TraceNode(
        new MethodSignature(className, methodName, List.of()),
        List.of(children),
        new TraceOutcome.Returned("\"ok\""),
        1_000_000L,
        1_000L,
        null,
        sc);
  }
}
