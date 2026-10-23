/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Reproducers for the 2026-10-22 review: expiry removes the entry it examined, then calls back. */
class AdversarialPerishableMapReviewTest {

  @Test
  void expiryMustNotRemoveAReplacementInsertedWhileItsCallbackRuns() throws Exception {
    var clock = new AtomicLong();
    var evicting = new CountDownLatch(1);
    var resume = new CountDownLatch(1);
    var map = pausingOnOld(clock, evicting, resume);
    map.put("span", "old");
    clock.set(200);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var eviction = executor.submit(() -> map.put("trigger", "other"));
      await(evicting);
      // Another caller ends the old span and publishes a new value for this key. The entry is
      // already gone: expiry removes before it calls back (owner ruling (b), 2026-10-22).
      assertThat(map.remove("span")).isNull();
      map.put("span", "replacement");
      resume.countDown();
      eviction.get(5, TimeUnit.SECONDS);

      assertThat(map.get("span"))
          .as("the expiry iterator may remove only the entry it actually examined")
          .isEqualTo("replacement");
    } finally {
      resume.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void expiryCallsBackOnceEvenWhenTheCallbackReentersTheMap() {
    var clock = new AtomicLong();
    var callbacks = new ArrayList<String>();
    var self = new AtomicReference<PerishableMap<String, String>>();
    self.set(
        new PerishableMap<>(
            10,
            Duration.ofNanos(100),
            value -> {
              callbacks.add(value);
              // The callback ends a span and, like any listener, may touch the map again.
              if (callbacks.size() == 1) {
                self.get().put("other", "fresh");
              }
            },
            clock::get));
    self.get().put("span", "old");
    clock.set(200);

    self.get().put("trigger", "x");

    assertThat(callbacks)
        .as("an expired entry is evicted once: removed first, called back only by the remover")
        .containsExactly("old");
  }

  /** A map whose eviction callback, on the value "old", signals and then waits to be resumed. */
  private static PerishableMap<String, String> pausingOnOld(
      AtomicLong clock, CountDownLatch evicting, CountDownLatch resume) {
    return new PerishableMap<>(
        10,
        Duration.ofNanos(100),
        value -> {
          if ("old".equals(value)) {
            evicting.countDown();
            await(resume);
          }
        },
        clock::get);
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS)).as("scheduled interleaving completed").isTrue();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError(interrupted);
    }
  }
}
