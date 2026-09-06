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
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext.TraceStack;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Same-package test for the live-child registry: the ledger that makes a worker's spans reportable
 * by the origin while the worker's scope is still open. Its guards and its ceiling are boundary
 * conditions an end-to-end test would need 10,000 threads to reach.
 */
class TraceStackLiveChildTest {

  @Test
  void aStackWithNoLiveChildrenReportsNoSpans() {
    var origin = new TraceStack(5);

    assertThat(origin.liveChildSpanIds()).isEmpty();
  }

  @Test
  void aLiveChildsSpansAreReportableByTheOrigin() {
    var origin = new TraceStack(5);
    var child = new TraceStack(5);
    var spanId = SpanId.generate();
    origin.registerLiveChild(child);

    child.pushActive(spanId);

    assertThat(origin.liveChildSpanIds()).containsExactly(spanId);
  }

  /**
   * Inverted from "own spans only" on 2026-08-31: hand-over and live visibility both carry the
   * child's whole reportable set, so the grandchild's calls reach the origin instead of stopping
   * one hop short of it. The two sides still name the same set, which is what stops a call from
   * appearing while a scope is open and vanishing when it closes.
   */
  @Test
  void aLiveChildContributesExactlyWhatItWillHandOverAtClose() {
    var origin = new TraceStack(5);
    var child = new TraceStack(5);
    var own = SpanId.generate();
    var fromGrandchild = SpanId.generate();
    child.pushActive(own);
    child.adopt(Set.of(fromGrandchild));
    origin.registerLiveChild(child);

    assertThat(origin.liveChildSpanIds()).containsExactlyInAnyOrder(own, fromGrandchild);
    assertThat(child.reportableSpanIds())
        .as("the live view and the hand-over set are one definition, not two")
        .isEqualTo(origin.liveChildSpanIds());
  }

  @Test
  void aLiveGrandchildReachesTheOriginThroughItsParent() {
    var origin = new TraceStack(5);
    var child = new TraceStack(5);
    var grandchild = new TraceStack(5);
    var childSpan = SpanId.generate();
    var grandchildSpan = SpanId.generate();
    child.pushActive(childSpan);
    grandchild.pushActive(grandchildSpan);
    origin.registerLiveChild(child);
    child.registerLiveChild(grandchild);

    assertThat(origin.liveChildSpanIds()).containsExactlyInAnyOrder(childSpan, grandchildSpan);
  }

  @Test
  void aChainOfLiveStacksIsWalkedToItsEnd() {
    var stacks = new TraceStack[6];
    var spans = new SpanId[6];
    for (var i = 0; i < stacks.length; i++) {
      stacks[i] = new TraceStack(5);
      spans[i] = SpanId.generate();
      stacks[i].pushActive(spans[i]);
      if (i > 0) {
        stacks[i - 1].registerLiveChild(stacks[i]);
      }
    }

    assertThat(stacks[0].reportableSpanIds()).containsExactlyInAnyOrder(spans);
  }

  @Test
  void aStackWithNothingOfItsOwnStillReportsWhatItsChildrenHave() {
    var origin = new TraceStack(5);
    var child = new TraceStack(5);
    var childSpan = SpanId.generate();
    child.pushActive(childSpan);
    origin.registerLiveChild(child);

    assertThat(origin.reportableSpanIds()).containsExactly(childSpan);
  }

  @Test
  void aSpanReachableThroughBothAdoptionAndALiveChildIsReportedOnce() {
    var origin = new TraceStack(5);
    var child = new TraceStack(5);
    var shared = SpanId.generate();
    child.pushActive(shared);
    origin.registerLiveChild(child);
    // The window scope close opens: adopt() has run, unregisterLiveChild() has not.
    origin.adopt(child.reportableSpanIds());

    assertThat(origin.reportableSpanIds())
        .as("the union across the adopt-then-unregister race must not double-count")
        .containsExactly(shared);
  }

  @Test
  void theCeilingOfTheReceivingStackBoundsWhatItAcceptsFromAWholeChain() {
    var origin = new TraceStack(2);
    var child = new TraceStack(5);
    child.pushActive(SpanId.generate());
    child.adopt(Set.of(SpanId.generate(), SpanId.generate()));

    // Three spans arrive as one batch; adoption is all-or-nothing, so the origin's own ceiling
    // refuses the chain whole rather than stranding a grandchild whose parent stayed out.
    origin.adopt(child.reportableSpanIds());

    assertThat(origin.adoptedSpanIds()).isEmpty();
    assertThat(origin.refusedScopeCount()).isOne();
    assertThat(origin.refusedSpanCount()).isEqualTo(3);
  }

  @Test
  void everyLiveChildContributes() {
    var origin = new TraceStack(5);
    var first = new TraceStack(5);
    var second = new TraceStack(5);
    var firstSpan = SpanId.generate();
    var secondSpan = SpanId.generate();
    first.pushActive(firstSpan);
    second.pushActive(secondSpan);

    origin.registerLiveChild(first);
    origin.registerLiveChild(second);

    assertThat(origin.liveChildSpanIds()).containsExactlyInAnyOrder(firstSpan, secondSpan);
  }

  @Test
  void unregisteringEndsTheContribution() {
    var origin = new TraceStack(5);
    var child = new TraceStack(5);
    child.pushActive(SpanId.generate());
    var registration = origin.registerLiveChild(child);

    origin.unregisterLiveChild(registration);

    assertThat(origin.liveChildSpanIds()).isEmpty();
  }

  @Test
  void unregisteringARefusedRegistrationIsHarmless() {
    var origin = new TraceStack(1);
    var kept = new TraceStack(1);
    kept.pushActive(SpanId.generate());
    origin.registerLiveChild(kept);

    origin.unregisterLiveChild(null);

    assertThat(origin.liveChildSpanIds()).hasSize(1);
  }

  @Test
  void unregisteringOnAStackThatNeverRegisteredIsHarmless() {
    var origin = new TraceStack(5);
    var other = new TraceStack(5);
    var registration = other.registerLiveChild(new TraceStack(5));

    origin.unregisterLiveChild(registration);

    assertThat(origin.liveChildSpanIds()).isEmpty();
  }

  @Test
  void theCeilingRefusesFurtherRegistrations() {
    var origin = new TraceStack(1);
    var kept = new TraceStack(1);
    var refused = new TraceStack(1);
    kept.pushActive(SpanId.generate());
    refused.pushActive(SpanId.generate());

    assertThat(origin.registerLiveChild(kept)).isNotNull();
    assertThat(origin.registerLiveChild(refused)).isNull();
    assertThat(origin.liveChildSpanIds()).hasSize(1);
  }

  @Test
  void aRefusedRegistrationIsNotCountedAsALostScope() {
    var origin = new TraceStack(1);
    origin.registerLiveChild(new TraceStack(1));

    origin.registerLiveChild(new TraceStack(1));

    // Nothing is lost by a refusal here — the spans still arrive through adopt() at scope close.
    assertThat(origin.refusedScopeCount()).isZero();
    assertThat(origin.refusedSpanCount()).isZero();
  }

  @Test
  void aCollectedChildContributesNothingAndFreesItsSlot() {
    var origin = new TraceStack(1);
    var collected = new TraceStack(1);
    collected.pushActive(SpanId.generate());
    // Clearing the handle is what the garbage collector does to a worker that died mid-scope.
    origin.registerLiveChild(collected).clear();

    assertThat(origin.liveChildSpanIds()).isEmpty();

    var replacement = new TraceStack(1);
    replacement.pushActive(SpanId.generate());
    assertThat(origin.registerLiveChild(replacement))
        .as("the cleared registration must have been pruned, or the ceiling leaks slots")
        .isNotNull();
  }

  @Test
  void aNullChildIsRejected() {
    var origin = new TraceStack(5);

    assertThatThrownBy(() -> origin.registerLiveChild(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Child stack is required");
  }

  @Test
  void aStackCannotRegisterItself() {
    var origin = new TraceStack(5);

    assertThatThrownBy(() -> origin.registerLiveChild(origin))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("A stack cannot be its own live child");
  }

  /**
   * The ruled invariant of item 44, under a capture that is actually running: scope close adopts
   * and then unregisters, so a capture in between must see the worker's spans through one route or
   * the other.
   *
   * <p><b>@edgeCase</b> The order the *reader* unions in is half of that guarantee. Reading the
   * adopted set first and the live children second lets a capture miss both: it reads adopted
   * before the hand-over adds to it, and live children after the hand-over removed the
   * registration. jcstress caught it at 0.12% of samples; this loop catches it in seconds.
   */
  @Test
  void aCaptureRacingAHandOverNeverSeesNeitherRoute() throws InterruptedException {
    for (int round = 0; round < 300; round++) {
      assertHandOverStaysVisible();
    }
  }

  private void assertHandOverStaysVisible() throws InterruptedException {
    var origin = new TraceStack(16);
    var child = new TraceStack(16);
    child.pushActive(SpanId.generate());
    var registration = origin.registerLiveChild(child);
    var handedOver = new AtomicBoolean();
    var missed = new AtomicBoolean();
    var spinning = new CountDownLatch(1);
    var capture = captureThread(origin, handedOver, missed, spinning);

    capture.start();
    spinning.await();
    origin.adopt(child.reportableSpanIds());
    origin.unregisterLiveChild(registration);
    handedOver.set(true);
    capture.join(2000);

    assertThat(missed).as("the worker's span was reportable through neither route").isFalse();
  }

  private static Thread captureThread(
      TraceStack origin, AtomicBoolean handedOver, AtomicBoolean missed, CountDownLatch spinning) {
    return new Thread(
        () -> {
          while (!handedOver.get()) {
            if (origin.reportableSpanIds().isEmpty()) {
              missed.set(true);
            }
            spinning.countDown();
          }
        });
  }
}
