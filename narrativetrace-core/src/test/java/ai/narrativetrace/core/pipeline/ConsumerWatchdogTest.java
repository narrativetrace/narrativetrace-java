/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConsumerWatchdogTest {

  private ConsumerWatchdog watchdog;

  @AfterEach
  void tearDown() {
    if (watchdog != null) {
      watchdog.close();
    }
  }

  @Test
  void watchdogFiresCallbackWhenStale() throws InterruptedException {
    long staleNanos = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(500);
    LongSupplier heartbeat = () -> staleNanos;
    var latch = new CountDownLatch(1);

    watchdog = new ConsumerWatchdog(heartbeat, 100, 50, latch::countDown);

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void watchdogDoesNotFireWhenHeartbeatIsFresh() throws InterruptedException {
    LongSupplier heartbeat = System::nanoTime;
    var latch = new CountDownLatch(1);

    watchdog = new ConsumerWatchdog(heartbeat, 5000, 50, latch::countDown);

    assertThat(latch.await(200, TimeUnit.MILLISECONDS)).isFalse();
  }

  @Test
  void watchdogStopsAfterClose() throws InterruptedException {
    long staleNanos = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(500);
    LongSupplier heartbeat = () -> staleNanos;
    var latch = new CountDownLatch(1);

    watchdog = new ConsumerWatchdog(heartbeat, 100, 50, latch::countDown);
    watchdog.close();

    assertThat(latch.await(200, TimeUnit.MILLISECONDS)).isFalse();
  }

  @Test
  void watchdogFiresCallbackRepeatedly() throws InterruptedException {
    long staleNanos = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(500);
    LongSupplier heartbeat = () -> staleNanos;
    var count = new AtomicInteger();
    var latch = new CountDownLatch(3);

    watchdog =
        new ConsumerWatchdog(
            heartbeat,
            100,
            50,
            () -> {
              count.incrementAndGet();
              latch.countDown();
            });

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(count.get()).isGreaterThanOrEqualTo(3);
  }

  @Test
  void watchdogToleratesCallbackException() throws InterruptedException {
    long staleNanos = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(500);
    LongSupplier heartbeat = () -> staleNanos;
    var count = new AtomicInteger();
    var latch = new CountDownLatch(2);

    watchdog =
        new ConsumerWatchdog(
            heartbeat,
            100,
            50,
            () -> {
              count.incrementAndGet();
              latch.countDown();
              throw new RuntimeException("boom");
            });

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(count.get()).isGreaterThanOrEqualTo(2);
  }

  @Test
  void watchdogIgnoresZeroHeartbeat() throws InterruptedException {
    LongSupplier heartbeat = () -> 0L;
    var latch = new CountDownLatch(1);

    watchdog = new ConsumerWatchdog(heartbeat, 100, 50, latch::countDown);

    assertThat(latch.await(300, TimeUnit.MILLISECONDS)).isFalse();
  }
}
