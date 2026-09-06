/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.api.event;

/**
 * What a trace is missing, and why.
 *
 * <p>INTENT: Every loss mode of the best-effort path reports through one type, so a reader is never
 * left guessing whether a short trace means "nothing happened" or "we dropped it". The synchronous
 * log stream is unaffected by any of them — it is the durable record, and a run that lost events
 * here still narrated them there.
 *
 * <p><b>@llmNote</b> A non-{@link #none()} loss makes the captured tree an incomplete view of the
 * run, not a wrong one: {@code droppedEvents} can also leave a span with no exit, which surfaces as
 * {@link TraceOutcome.Incomplete}, so an outcome — not only a branch — may be missing.
 *
 * @param droppedEvents events the bounded buffer shed under load (process-wide since start)
 * @param refusedScopes worker scopes whose spans were refused whole because the adoption cap was
 *     full — each one is an async subtree absent from the tree
 * @param refusedSpans spans lost to those refusals
 * @param discardedSpans spans dropped because the request that owned them had already ended — an
 *     async worker that finished after its request reset (process-wide since start)
 */
public record TraceLoss(
    long droppedEvents, long refusedScopes, long refusedSpans, long discardedSpans) {

  private static final TraceLoss NONE = new TraceLoss(0L, 0L, 0L, 0L);

  /** Validates that no count is negative. */
  public TraceLoss {
    if (droppedEvents < 0 || refusedScopes < 0 || refusedSpans < 0 || discardedSpans < 0) {
      throw new IllegalArgumentException("Loss counts must not be negative");
    }
  }

  /**
   * The three loss modes a capture can suffer, with nothing discarded after the fact.
   *
   * <p>INTENT: Keeps every existing reading and every fixture that predates {@link
   * #discardedSpans()} compiling and meaning the same thing — a capture reports what it lost, and
   * late-discarded work is a fourth question, not a restatement of the first three.
   *
   * @param droppedEvents events the bounded buffer shed under load
   * @param refusedScopes worker scopes refused whole by the adoption cap
   * @param refusedSpans spans lost to those refusals
   */
  public TraceLoss(long droppedEvents, long refusedScopes, long refusedSpans) {
    this(droppedEvents, refusedScopes, refusedSpans, 0L);
  }

  /** The shared "nothing was lost" instance. */
  public static TraceLoss none() {
    return NONE;
  }

  /**
   * True when something was lost, i.e. the captured trace is an incomplete view of the run.
   *
   * <p><b>@edgeCase</b> {@link #discardedSpans()} is deliberately not part of this. A discard
   * happens after the request that owned the work has ended, so no tree anyone renders is missing
   * anything it could have contained — the counter is an operational signal that async work is
   * outliving its request, not a statement about the narrative in hand. Every renderer's footer
   * hangs off this method, and a footer crying "incomplete" over a previous request's background
   * tail would be a false alarm on a complete trace.
   */
  public boolean any() {
    return droppedEvents > 0 || refusedScopes > 0;
  }

  /** Sums two losses, for reporting a suite total over per-scenario readings. */
  public TraceLoss plus(TraceLoss other) {
    return new TraceLoss(
        droppedEvents + other.droppedEvents,
        refusedScopes + other.refusedScopes,
        refusedSpans + other.refusedSpans,
        discardedSpans + other.discardedSpans);
  }

  /**
   * The difference between this reading and an earlier one, floored at zero — the loss that
   * happened between them. Process-wide counters only grow, so a caller brackets a scenario with
   * two readings to attribute loss to it.
   */
  public TraceLoss since(TraceLoss earlier) {
    return new TraceLoss(
        Math.max(0L, droppedEvents - earlier.droppedEvents),
        Math.max(0L, refusedScopes - earlier.refusedScopes),
        Math.max(0L, refusedSpans - earlier.refusedSpans),
        Math.max(0L, discardedSpans - earlier.discardedSpans));
  }
}
