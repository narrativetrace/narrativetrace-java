/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.annotation.NarrativeSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADV-2026-10-23-2 — the rendering guard must stay up for the whole of one walk: a sanctioned hook
 * that re-enters a public rendering entry point may not clear it for the rest of the walk.
 *
 * <p>INTENT: {@link RenderingGuard} is what makes "a hook that reaches traced code produces no
 * span" true — {@code AgentRuntime.isActive()} reads it before anything else on every instrumented
 * call. Its class documentation argues one boolean is enough because "{@code ValueRenderer}'s
 * public entry points never observe re-entry on the same thread: a reflective call into a woven
 * accessor made while the guard is active is stopped at the instrumented method's own gate before
 * it can call back into {@code ValueRenderer}". A woven accessor is not the only door. The
 * narrative-summary hook is user code the renderer invokes on purpose, and anything that code
 * reaches which renders a value of its own — a proxy-traced collaborator, an SLF4J listener, a
 * {@code @Narrated} template, or simply another {@code ValueRenderer} — runs {@code enter()}/{@code
 * leave()} as a pair, and {@code leave()} removes the thread-local rather than restoring the outer
 * value.
 *
 * <p><b>@llmNote</b> The guard is therefore down for every reflective read the outer walk still has
 * to make: the sibling fields after the hook, and every level below them. On the agent path those
 * reads reach woven bytecode through an open gate, which is the exact defect {@code
 * RenderReentrancyGuardTest} exists to prevent. A counter — or save-and-restore — is what the
 * nesting this hook allows actually needs.
 */
class RenderingGuardNestingTest {

  private final ValueRenderer renderer = new ValueRenderer();

  /** A hook that renders something of its own, the way traced code reached from a hook does. */
  static final class ReEnteringSummary {

    @NarrativeSummary
    public String toNarrativeSummary() {
      new ValueRenderer().render("a value rendered from inside the hook");
      return "re-entered";
    }
  }

  /** A hook that reports whether the guard was still up when the walk reached it. */
  static final class GuardProbe {

    boolean called;

    boolean guardActive;

    @NarrativeSummary
    public String toNarrativeSummary() {
      called = true;
      guardActive = RenderingGuard.isActive();
      return "probed";
    }
  }

  /** Declaration order is the field-walk order, so the probe is read after the re-entry. */
  @SuppressWarnings("unused")
  static final class Siblings {

    private final ReEnteringSummary first;

    private final GuardProbe second;

    Siblings(ReEnteringSummary first, GuardProbe second) {
      this.first = first;
      this.second = second;
    }
  }

  @Test
  @DisplayName("ADV-2026-10-23-2: a hook that re-enters rendering leaves the guard up for the rest")
  void aHookThatReEntersRenderingLeavesTheGuardUpForTheRestOfTheWalk() {
    var probe = new GuardProbe();

    renderer.render(new Siblings(new ReEnteringSummary(), probe));

    assertThat(probe.called).as("the probe hook must have been reached at all").isTrue();
    assertThat(probe.guardActive)
        .as("the guard must still be up for the sibling read that follows the hook")
        .isTrue();
  }

  @Test
  @DisplayName(
      "B-186: an inner enter/leave pair restores the outer guard, the last leave clears it")
  void anInnerPairRestoresTheOuterGuardAndTheLastLeaveClearsIt() {
    RenderingGuard.enter();
    try {
      RenderingGuard.enter();
      RenderingGuard.leave();
      assertThat(RenderingGuard.isActive()).as("the outer entry is still open").isTrue();
    } finally {
      RenderingGuard.leave();
    }
    assertThat(RenderingGuard.isActive()).as("every entry has left").isFalse();
  }

  @Test
  @DisplayName("B-186: a leave with no open entry never drives the guard below zero")
  void anUnpairedLeaveNeverDrivesTheGuardBelowZero() {
    RenderingGuard.leave();
    RenderingGuard.enter();
    try {
      assertThat(RenderingGuard.isActive()).as("one entry after a stray leave is open").isTrue();
    } finally {
      RenderingGuard.leave();
    }
    assertThat(RenderingGuard.isActive()).isFalse();
  }
}
