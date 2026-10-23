/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.testng.annotations.Test;

/**
 * Four tests that are all inside their traced call at the same moment — a latch, not a sleep, holds
 * each one open until the other three have entered theirs — so a capture that leaked across threads
 * would show another test's call.
 */
public class ParallelFixture {

  static final int TESTS = 4;
  static volatile CountDownLatch allInside = new CountDownLatch(TESTS);

  @Test
  public void alphaReserves() throws InterruptedException {
    overlapping("AlphaService", "reserve");
  }

  @Test
  public void bravoReserves() throws InterruptedException {
    overlapping("BravoService", "reserve");
  }

  @Test
  public void charlieReserves() throws InterruptedException {
    overlapping("CharlieService", "reserve");
  }

  @Test
  public void deltaReserves() throws InterruptedException {
    overlapping("DeltaService", "reserve");
  }

  private static void overlapping(String service, String method) throws InterruptedException {
    var context = NarrativeTraceListener.context();
    context.enterMethod(
        new ai.narrativetrace.api.event.MethodSignature(service, method, java.util.List.of()));
    allInside.countDown();
    if (!allInside.await(30, TimeUnit.SECONDS)) {
      throw new AssertionError("the four tests never overlapped");
    }
    context.exitMethodWithReturn("held");
  }
}
