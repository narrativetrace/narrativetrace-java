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
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.ResourceIdentity;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.render.TraceNamer;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.List;
import org.junit.jupiter.api.Test;

class TraceTreeCanonicalMapperTest {

  @Test
  void mapsATreeToEnterAndExitEntriesDepthFirst() {
    var child =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"TXN-1\""));
    var root =
        new TraceNode(
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(new ParameterCapture("customerId", "\"C-1\"", false))),
            List.of(child),
            new TraceOutcome.Returned("\"order-1\""));

    var entries = TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of(root)));

    assertThat(entries).hasSize(4);
    assertThat(entries.get(0).ntEventType()).isEqualTo("method_enter");
    assertThat(entries.get(0).codeFunction()).isEqualTo("placeOrder");
    assertThat(entries.get(0).parentSpanId()).isNull();
    assertThat(entries.get(0).ntParameters()).hasSize(1);
    assertThat(entries.get(1).ntEventType()).isEqualTo("method_enter");
    assertThat(entries.get(1).codeFunction()).isEqualTo("charge");
    assertThat(entries.get(1).parentSpanId()).isEqualTo(entries.get(0).spanId());
    assertThat(entries.get(2).ntEventType()).isEqualTo("method_exit");
    assertThat(entries.get(2).spanId()).isEqualTo(entries.get(1).spanId());
    assertThat(entries.get(2).codeFunction()).isEqualTo("charge");
    assertThat(entries.get(2).ntOutcome()).isEqualTo("success");
    assertThat(entries.get(2).ntReturnValue()).isEqualTo("\"TXN-1\"");
    assertThat(entries.get(3).ntEventType()).isEqualTo("method_exit");
    assertThat(entries.get(3).spanId()).isEqualTo(entries.get(0).spanId());
  }

  @Test
  void failureOutcomeCarriesExceptionTypeAndVerbatimMessage() {
    var root =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Threw(
                new IllegalStateException("balance 12.50 below required 74.97")));

    var entries = TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of(root)));

    var exit = entries.get(1);
    assertThat(exit.ntOutcome()).isEqualTo("failure");
    assertThat(exit.level()).isEqualTo("error");
    assertThat(exit.exceptionType()).isEqualTo("IllegalStateException");
    assertThat(exit.exceptionMessage()).isEqualTo("balance 12.50 below required 74.97");
    assertThat(exit.ntExceptionPackage()).isEqualTo("java.lang");
  }

  @Test
  void declaringPackageTravelsOnEnterAndExitEntries() {
    var root =
        new TraceNode(
            new MethodSignature(
                "PaymentService", "charge", List.of(), null, null, null, "com.acme.payments"),
            List.of(),
            new TraceOutcome.Returned("\"TXN-1\""));

    var entries = TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of(root)));

    assertThat(entries.get(0).ntPackage()).isEqualTo("com.acme.payments");
    assertThat(entries.get(1).ntPackage()).isEqualTo("com.acme.payments");
    assertThat(entries.get(0).ntSchemaVersion()).isEqualTo("1.2");
  }

  @Test
  void rawNarrationTemplateTravelsOnTheEnterEntry() {
    var root =
        new TraceNode(
            new MethodSignature(
                "OverdraftService",
                "openAccount",
                List.of(),
                "Opening for C-1",
                null,
                "Opening for {customerId}"),
            List.of(),
            new TraceOutcome.Returned("\"ok\""));

    var entries = TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of(root)));

    assertThat(entries.get(0).ntNarrationTemplate()).isEqualTo("Opening for {customerId}");
  }

  @Test
  void rejectsANullTree() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> TraceTreeCanonicalMapper.fromTree(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nodesWithARealSpanContextKeepTheirTraceAndSpanIds() {
    var spanContext =
        ai.narrativetrace.api.event.SpanContext.builder(
                ai.narrativetrace.api.event.TraceId.of("0123456789abcdef0123456789abcdef"),
                ai.narrativetrace.api.event.SpanId.of("00000000000000aa"))
            .build();
    var root =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            0L,
            0L,
            null,
            spanContext);

    var entries = TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of(root)));

    assertThat(entries.get(0).spanId()).isEqualTo("00000000000000aa");
    assertThat(entries.get(0).traceId()).isEqualTo("0123456789abcdef0123456789abcdef");
  }

  @Test
  void redactedParametersStayRedactedInTheCanonicalEntry() {
    var root =
        new TraceNode(
            new MethodSignature(
                "AuthService",
                "login",
                List.of(new ParameterCapture("password", "\"hunter2\"", true))),
            List.of(),
            new TraceOutcome.Returned("\"ok\""));

    var entries = TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of(root)));

    assertThat(entries.get(0).ntParameters().get(0).value()).isEqualTo("[REDACTED]");
    assertThat(entries.get(0).ntParameters().get(0).redacted()).isTrue();
  }

  @Test
  void incompleteOutcomeMapsToIncomplete() {
    var root =
        new TraceNode(
            new MethodSignature("JobService", "runJob", List.of()),
            List.of(),
            new TraceOutcome.Incomplete());

    var entries = TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of(root)));

    assertThat(entries.get(1).ntOutcome()).isEqualTo("incomplete");
  }

  // ── identity of context-free trees (item 26a; eager generation, 2026-08-30) ───

  private static TraceNode plainRoot() {
    return new TraceNode(
        new MethodSignature("OrderService", "placeOrder", List.of()),
        List.of(
            new TraceNode(
                new MethodSignature("PaymentService", "charge", List.of()),
                List.of(),
                new TraceOutcome.Returned("\"TXN-1\""))),
        new TraceOutcome.Returned("\"order-1\""));
  }

  @Test
  void aTreeWithoutSpanContextGetsOneGeneratedTraceIdOnEveryEntry() {
    var entries = TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of(plainRoot())));

    assertThat(entries).isNotEmpty();
    assertThat(entries).allSatisfy(e -> assertThat(e.traceId()).isNotNull());
    assertThat(entries).extracting(CanonicalEntry::traceId).containsOnly(entries.get(0).traceId());
  }

  @Test
  void theGeneratedTraceIdIsAWellFormedW3cIdAndNotAllZeroes() {
    var entries = TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of(plainRoot())));

    assertThat(entries.get(0).traceId()).matches("^[0-9a-f]{32}$");
    assertThat(entries.get(0).traceId()).isNotEqualTo("0".repeat(32));
  }

  @Test
  void twoIndependentContextFreeTreesNeverShareATraceId() {
    // Owner decision 2026-08-30: identity is generated eagerly, always. A shared constant made
    // two unrelated captures indistinguishable to every consumer downstream; describing fields
    // (story, chapter) and the structural span ids stay derived and deterministic.
    var first = TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of(plainRoot())));
    var second = TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of(plainRoot())));

    assertThat(first.get(0).traceId()).isNotEqualTo(second.get(0).traceId());
    assertThat(first.get(0).ntStoryId()).isEqualTo(second.get(0).ntStoryId());
    assertThat(first.get(0).spanId()).isEqualTo(second.get(0).spanId());
  }

  @Test
  void aContextFreeTreeAdoptsTheTraceIdItsCaptureAssigned() {
    // Rung 2 of the ladder: the capture knows the trace even when no node kept its context,
    // so nothing is generated here.
    var assigned = TraceId.of("0af7651916cd43dd8448eb211c80319c");

    var entries =
        TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of(plainRoot()), assigned));

    assertThat(entries).extracting(CanonicalEntry::traceId).containsOnly(assigned.toString());
    assertThat(entries)
        .extracting(CanonicalEntry::ntTraceName)
        .containsOnly(TraceNamer.name(assigned.value()));
  }

  @Test
  void everyEntryOfAContextFreeTreeCarriesTheTraceNameOfItsOwnTraceId() {
    var entries = TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of(plainRoot())));

    assertThat(entries)
        .allSatisfy(
            e ->
                assertThat(e.ntTraceName())
                    .isEqualTo(TraceNamer.name(e.traceId()))
                    .matches("^[a-z]+ [a-z]+ [a-z]+$"));
  }

  @Test
  void storyIdIsDerivedFromTheFirstRootCallAndChapterIdEqualsIt() {
    var entries = TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of(plainRoot())));

    assertThat(entries)
        .allSatisfy(
            e -> {
              assertThat(e.ntStoryId()).isEqualTo("OrderService.placeOrder");
              assertThat(e.ntChapterId()).isEqualTo("OrderService.placeOrder");
            });
  }

  @Test
  void aRealSpanContextWinsOverTheGeneratedIdentity() {
    var sc =
        SpanContext.builder(
                TraceId.of("0af7651916cd43dd8448eb211c80319c"), SpanId.of("b7ad6b7169203331"))
            .storyId("Real.story")
            .chapterId("Real.chapter")
            .build();
    var root =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"ok\""),
            1_000_000L,
            System.nanoTime(),
            null,
            sc);

    var entries = TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of(root)));

    assertThat(entries.get(0).traceId()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
    assertThat(entries.get(0).ntStoryId()).isEqualTo("Real.story");
    assertThat(entries.get(0).ntChapterId()).isEqualTo("Real.chapter");
  }

  @Test
  void aNodeThatKeptItsOwnSpanContextReportsThatContextsIdentityNotTheTreesInherited() {
    // A malformed tree -- two nodes naming two traces -- is not silently "repaired" by
    // stamping the inherited identity over a node that carries its own. The node's own
    // context is the authority for that node; only nodes without one adopt the tree's.
    var rootSc =
        SpanContext.builder(
                TraceId.of("0af7651916cd43dd8448eb211c80319c"), SpanId.of("b7ad6b7169203331"))
            .storyId("Root.story")
            .chapterId("Root.chapter")
            .build();
    var childSc =
        SpanContext.builder(
                TraceId.of("4bf92f3577b34da6a3ce929d0e0e4736"), SpanId.of("00f067aa0ba902b7"))
            .storyId("Child.story")
            .chapterId("Child.chapter")
            .build();
    var child =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"TXN-1\""),
            1_000_000L,
            1_000L,
            null,
            childSc);
    var root =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child),
            new TraceOutcome.Returned("\"ok\""),
            2_000_000L,
            1_000L,
            null,
            rootSc);

    var entries = TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of(root)));

    // Order: root enter, child enter, child exit, root exit.
    assertThat(entries.get(1).traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    assertThat(entries.get(1).ntStoryId()).isEqualTo("Child.story");
    assertThat(entries.get(1).ntChapterId()).isEqualTo("Child.chapter");
    assertThat(entries.get(0).traceId()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
    assertThat(entries.get(0).ntStoryId()).isEqualTo("Root.story");
    assertThat(entries.get(0).ntChapterId()).isEqualTo("Root.chapter");
    // The trace name follows whichever trace id the entry actually carries.
    assertThat(entries)
        .allSatisfy(e -> assertThat(e.ntTraceName()).isEqualTo(TraceNamer.name(e.traceId())));
  }

  @Test
  void anEmptyTreeYieldsNoEntriesAndDoesNotThrow() {
    assertThat(TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of()))).isEmpty();
  }

  @Test
  void aContextFreeChildInheritsTheTracesRealIdentityRatherThanAGeneratedOne() {
    // Inheritance beats generation: one tree is one trace, so a node that lost its
    // context must not be stamped with a second trace id.
    var sc =
        SpanContext.builder(
                TraceId.of("0af7651916cd43dd8448eb211c80319c"), SpanId.of("b7ad6b7169203331"))
            .storyId("Real.story")
            .chapterId("Real.chapter")
            .build();
    var child =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"TXN-1\""));
    var root =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child),
            new TraceOutcome.Returned("\"ok\""),
            1_000_000L,
            System.nanoTime(),
            null,
            sc);

    var entries = TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of(root)));

    assertThat(entries)
        .allSatisfy(
            e -> {
              assertThat(e.traceId()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
              assertThat(e.ntStoryId()).isEqualTo("Real.story");
              assertThat(e.ntChapterId()).isEqualTo("Real.chapter");
            });
  }

  @Test
  void aRealIdentityDeeperInTheTreeIsStillInheritedByContextFreeSiblings() {
    var sc =
        SpanContext.builder(
                TraceId.of("0af7651916cd43dd8448eb211c80319c"), SpanId.of("b7ad6b7169203331"))
            .storyId("Real.story")
            .build();
    var contextual =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"TXN-1\""),
            1_000_000L,
            System.nanoTime(),
            null,
            sc);
    var root =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(contextual),
            new TraceOutcome.Returned("\"ok\""));

    var entries = TraceTreeCanonicalMapper.fromTree(new DefaultTraceTree(List.of(root)));

    assertThat(entries)
        .extracting(CanonicalEntry::traceId)
        .containsOnly("0af7651916cd43dd8448eb211c80319c");
    assertThat(entries).allSatisfy(e -> assertThat(e.ntStoryId()).isEqualTo("Real.story"));
  }

  // ── item 26b: every trace-scoped field survives a MIXED tree ─────────────────

  /** A tree whose root carries full identity and whose child carries none. */
  private static DefaultTraceTree mixedTree() {
    var sc =
        SpanContext.builder(
                TraceId.of("0af7651916cd43dd8448eb211c80319c"), SpanId.of("b7ad6b7169203331"))
            .serviceName("order-service")
            .environment("production")
            .storyId("Real.story")
            .chapterId("Real.chapter")
            .resourceIdentity(new ResourceIdentity("web-1", 4242L, "17.0.10+7"))
            .build();
    var child =
        new TraceNode(
            new MethodSignature("PaymentService", "charge", List.of()),
            List.of(),
            new TraceOutcome.Returned("\"TXN-1\""));
    var root =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(child),
            new TraceOutcome.Returned("\"ok\""),
            1_000_000L,
            System.nanoTime(),
            null,
            sc);
    return new DefaultTraceTree(List.of(root));
  }

  @Test
  void mixedTreeSharesOneServiceRatherThanFallingBackOnContextFreeNodes() {
    var entries = TraceTreeCanonicalMapper.fromTree(mixedTree());

    assertThat(entries).extracting(CanonicalEntry::service).containsOnly("order-service");
  }

  @Test
  void mixedTreeSharesOneEnvironment() {
    var entries = TraceTreeCanonicalMapper.fromTree(mixedTree());

    assertThat(entries).extracting(CanonicalEntry::environment).containsOnly("production");
  }

  @Test
  void mixedTreeSharesOneResourceIdentity() {
    var entries = TraceTreeCanonicalMapper.fromTree(mixedTree());

    assertThat(entries).extracting(CanonicalEntry::hostName).containsOnly("web-1");
    assertThat(entries).extracting(CanonicalEntry::processPid).containsOnly(4242L);
    assertThat(entries).extracting(CanonicalEntry::runtimeVersion).containsOnly("17.0.10+7");
  }

  @Test
  void mixedTreeSharesOneTraceIdentity() {
    var entries = TraceTreeCanonicalMapper.fromTree(mixedTree());

    assertThat(entries)
        .extracting(CanonicalEntry::traceId)
        .containsOnly("0af7651916cd43dd8448eb211c80319c");
    assertThat(entries).extracting(CanonicalEntry::ntStoryId).containsOnly("Real.story");
    assertThat(entries).extracting(CanonicalEntry::ntChapterId).containsOnly("Real.chapter");
  }

  @Test
  void mixedTreeSharesOneTraceName() {
    var entries = TraceTreeCanonicalMapper.fromTree(mixedTree());

    assertThat(entries).extracting(CanonicalEntry::ntTraceName).doesNotContainNull();
    assertThat(entries)
        .extracting(CanonicalEntry::ntTraceName)
        .containsOnly(entries.get(0).ntTraceName());
  }

  @Test
  void spanIdsStayUniquePerSpanAndAreNeverInherited() {
    var entries = TraceTreeCanonicalMapper.fromTree(mixedTree());

    // Root enter/exit share one span id, child enter/exit another -- two distinct spans.
    assertThat(entries).extracting(CanonicalEntry::spanId).doesNotContainNull();
    assertThat(entries.stream().map(CanonicalEntry::spanId).distinct().toList()).hasSize(2);
  }

  @Test
  void parentSpanIdIsStructuralNotInherited() {
    var entries = TraceTreeCanonicalMapper.fromTree(mixedTree());

    // Entry order is: root enter, child enter, child exit, root exit.
    assertThat(entries.get(0).parentSpanId()).isNull();
    assertThat(entries.get(1).parentSpanId()).isEqualTo(entries.get(0).spanId());
  }
}
