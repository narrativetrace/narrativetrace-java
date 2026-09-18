/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent.sample;

import ai.narrativetrace.api.annotation.NarrativeSummary;
import java.util.concurrent.CountDownLatch;

/**
 * Fixture record for the per-thread render-reentrancy test. Its {@code @NarrativeSummary} method
 * can be paused mid-invocation via static coordination latches, so a test can force a genuine
 * overlap between one thread's in-progress value rendering and another thread's unrelated woven
 * call.
 *
 * <p><b>@llmNote</b> Rendering reads a record's own state directly rather than through its
 * generated accessor, so {@code sku()} itself is never invoked by rendering and cannot be the pause
 * point any more — {@code @NarrativeSummary} is the one hook rendering still runs reflectively, and
 * takes precedence over the component walk, so it is the pause point here instead. The coordination
 * fields are static, not record components — {@code getRecordComponents()} only sees {@code
 * sku}/{@code quantity}, so they are never themselves rendered. Tests must null both fields out (or
 * replace them) once done so later tests are not affected by a stale latch.
 */
public record PausableLine(String sku, int quantity) {

  public static volatile CountDownLatch enteredRendering;
  public static volatile CountDownLatch releaseRendering;

  @NarrativeSummary
  public String pausableSummary() {
    var entered = enteredRendering;
    var release = releaseRendering;
    if (entered != null) {
      entered.countDown();
    }
    if (release != null) {
      try {
        release.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    return sku;
  }
}
