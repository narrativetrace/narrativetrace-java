/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext.TraceStack;
import ai.narrativetrace.core.context.catdd.ContractVerifiable;
import ai.narrativetrace.core.context.catdd.InvariantCheckExtension;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(InvariantCheckExtension.class)
class TraceStackTest implements ContractVerifiable<TraceStack> {

  private TraceStack stack;

  @Override
  public TraceStack subject() {
    return stack;
  }

  @Override
  public boolean checkInvariant() {
    return stack == null || stack.invariant();
  }

  @BeforeEach
  void setUp() {
    stack = new TraceStack();
  }

  // ---------------------------------------------------------------
  // Empty stack behavior
  // ---------------------------------------------------------------

  @Test
  void newStackIsEmpty() {
    assertThat(stack.isEmpty()).isTrue();
  }

  @Test
  void newStackPeekReturnsNull() {
    assertThat(stack.peekActive()).isNull();
  }

  @Test
  void newStackHasNoKnownSpanIds() {
    assertThat(stack.knownSpanIds()).isEmpty();
  }

  @Test
  void newStackHasNullTraceId() {
    assertThat(stack.traceId()).isNull();
  }

  @Test
  void newStackHasNullStoryId() {
    assertThat(stack.storyId()).isNull();
    assertThat(stack.chapterId()).isNull();
  }

  // ---------------------------------------------------------------
  // Push and peek
  // ---------------------------------------------------------------

  @Test
  void pushMakesStackNonEmpty() {
    stack.pushActive(SpanId.generate());
    assertThat(stack.isEmpty()).isFalse();
  }

  @Test
  void peekReturnsLastPushed() {
    SpanId first = SpanId.generate();
    SpanId second = SpanId.generate();
    stack.pushActive(first);
    stack.pushActive(second);
    assertThat(stack.peekActive()).isEqualTo(second);
  }

  @Test
  void pushAddsToKnownSpanIds() {
    SpanId id = SpanId.generate();
    stack.pushActive(id);
    assertThat(stack.knownSpanIds()).containsExactly(id);
  }

  @Test
  void pushNullSpanIdThrows() {
    assertThatThrownBy(() -> stack.pushActive(null)).isInstanceOf(IllegalArgumentException.class);
  }

  // ---------------------------------------------------------------
  // Pop
  // ---------------------------------------------------------------

  @Test
  void popReturnsLastPushed() {
    SpanId id = SpanId.generate();
    stack.pushActive(id);
    assertThat(stack.popActive()).isEqualTo(id);
  }

  @Test
  void popRemovesFromActiveButKeepsInKnown() {
    SpanId id = SpanId.generate();
    stack.pushActive(id);
    stack.popActive();
    assertThat(stack.isEmpty()).isTrue();
    assertThat(stack.knownSpanIds()).containsExactly(id);
  }

  @Test
  void popFromEmptyStackThrows() {
    assertThatThrownBy(() -> stack.popActive()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void popIsLifo() {
    SpanId first = SpanId.generate();
    SpanId second = SpanId.generate();
    stack.pushActive(first);
    stack.pushActive(second);
    assertThat(stack.popActive()).isEqualTo(second);
    assertThat(stack.popActive()).isEqualTo(first);
  }

  // ---------------------------------------------------------------
  // Detach
  // ---------------------------------------------------------------

  @Test
  void detachRemovesFromActiveButKeepsInKnown() {
    SpanId id = SpanId.generate();
    stack.pushActive(id);
    stack.detach(id);
    assertThat(stack.isEmpty()).isTrue();
    assertThat(stack.knownSpanIds()).containsExactly(id);
  }

  @Test
  void detachMiddleElementPreservesOrder() {
    SpanId first = SpanId.generate();
    SpanId second = SpanId.generate();
    SpanId third = SpanId.generate();
    stack.pushActive(first);
    stack.pushActive(second);
    stack.pushActive(third);
    stack.detach(second);
    assertThat(stack.peekActive()).isEqualTo(third);
    stack.popActive();
    assertThat(stack.peekActive()).isEqualTo(first);
  }

  @Test
  void detachNonexistentSpanIsNoOp() {
    SpanId id = SpanId.generate();
    stack.pushActive(id);
    stack.detach(SpanId.generate());
    assertThat(stack.isEmpty()).isFalse();
  }

  // ---------------------------------------------------------------
  // TraceId
  // ---------------------------------------------------------------

  @Test
  void setTraceIdIsRetained() {
    TraceId id = TraceId.generate();
    stack.setTraceId(id);
    assertThat(stack.traceId()).isEqualTo(id);
  }

  @Test
  void setNullTraceIdThrows() {
    assertThatThrownBy(() -> stack.setTraceId(null)).isInstanceOf(IllegalArgumentException.class);
  }

  // ---------------------------------------------------------------
  // StoryId / ChapterId derivation
  // ---------------------------------------------------------------

  @Test
  void deriveStoryIdSetsStoryAndChapter() {
    stack.deriveStoryIdIfAbsent("OrderService", "placeOrder");
    assertThat(stack.storyId()).isEqualTo("OrderService.placeOrder");
    assertThat(stack.chapterId()).isEqualTo("OrderService.placeOrder");
  }

  @Test
  void deriveStoryIdOnlyOnce() {
    stack.deriveStoryIdIfAbsent("OrderService", "placeOrder");
    stack.deriveStoryIdIfAbsent("PaymentService", "charge");
    assertThat(stack.storyId()).isEqualTo("OrderService.placeOrder");
  }

  @Test
  void deriveStoryIdRejectsNullClassName() {
    assertThatThrownBy(() -> stack.deriveStoryIdIfAbsent(null, "method"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deriveStoryIdRejectsBlankClassName() {
    assertThatThrownBy(() -> stack.deriveStoryIdIfAbsent("  ", "method"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deriveStoryIdRejectsNullMethodName() {
    assertThatThrownBy(() -> stack.deriveStoryIdIfAbsent("Class", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deriveStoryIdRejectsBlankMethodName() {
    assertThatThrownBy(() -> stack.deriveStoryIdIfAbsent("Class", ""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---------------------------------------------------------------
  // Scoped and snapshot parent span ids
  // ---------------------------------------------------------------

  @Test
  void scopedParentSpanIdDefaultsToNull() {
    assertThat(stack.scopedParentSpanId()).isNull();
  }

  @Test
  void setScopedParentSpanIdIsRetained() {
    SpanId id = SpanId.generate();
    stack.setScopedParentSpanId(id);
    assertThat(stack.scopedParentSpanId()).isEqualTo(id);
  }

  @Test
  void setScopedParentSpanIdToNullClears() {
    SpanId id = SpanId.generate();
    stack.setScopedParentSpanId(id);
    stack.setScopedParentSpanId(null);
    assertThat(stack.scopedParentSpanId()).isNull();
  }

  @Test
  void snapshotParentSpanIdDefaultsToNull() {
    assertThat(stack.snapshotParentSpanId()).isNull();
  }

  @Test
  void setSnapshotParentSpanIdIsRetained() {
    SpanId id = SpanId.generate();
    stack.setSnapshotParentSpanId(id);
    assertThat(stack.snapshotParentSpanId()).isEqualTo(id);
  }

  // ---------------------------------------------------------------
  // Known span ids accumulation
  // ---------------------------------------------------------------

  @Test
  void knownSpanIdsAccumulatesAcrossPushPopDetach() {
    SpanId a = SpanId.generate();
    SpanId b = SpanId.generate();
    SpanId c = SpanId.generate();
    stack.pushActive(a);
    stack.pushActive(b);
    stack.popActive();
    stack.pushActive(c);
    stack.detach(c);
    assertThat(stack.knownSpanIds()).containsExactlyInAnyOrder(a, b, c);
  }

  /**
   * The set capture filters events with is a snapshot, not a view onto the live sets. It has to be:
   * `captureTrace()` iterates it while worker threads may still be publishing into the stack behind
   * it, and a view would let one of them mutate the set mid-iteration. Pinned here because the
   * capture path now takes exactly one copy — this one — where it used to take three, and a future
   * "one fewer copy" would land on the wrong side of this property.
   */
  @Test
  void reportableSpanIdsIsASnapshotAndNotALiveView() {
    SpanId first = SpanId.generate();
    stack.pushActive(first);
    var snapshot = stack.reportableSpanIds();

    stack.pushActive(SpanId.generate());
    stack.adopt(Set.of(SpanId.generate()));

    assertThat(snapshot).containsExactly(first);
    assertThat(stack.reportableSpanIds()).hasSize(3);
  }

  @Test
  void knownSpanIdsReturnsDefensiveCopy() {
    SpanId id = SpanId.generate();
    stack.pushActive(id);
    var snapshot = stack.knownSpanIds();
    stack.pushActive(SpanId.generate());
    assertThat(snapshot).hasSize(1);
  }
}
