/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.notify.domain;

import ai.narrativetrace.api.annotation.Narrated;
import ai.narrativetrace.api.annotation.OnError;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * Traced core of the notify process: a configurable delay, then a configurable share of simulated
 * failures — the whole replacement for the ecommerce example's real HTTP call to a third-party
 * service.
 *
 * <p>Woven by the java agent (see {@code compose.yaml}'s {@code packages=} argument), not by a
 * proxy or annotation processor — the {@link Narrated}/{@link OnError} annotations only supply the
 * narration templates the agent already knows how to read.
 */
public class NotificationProcessor {

  private final NotifyProperties properties;
  private final LongSupplier delayMillisSource;
  private final IntSupplier failureRollSource;

  public NotificationProcessor(NotifyProperties properties) {
    this(
        properties,
        () ->
            ThreadLocalRandom.current()
                .nextLong(properties.minDelayMillis(), properties.maxDelayMillis() + 1),
        () -> ThreadLocalRandom.current().nextInt(100));
  }

  /** Package-private: lets tests fix the delay and the failure roll deterministically. */
  NotificationProcessor(
      NotifyProperties properties, LongSupplier delayMillisSource, IntSupplier failureRollSource) {
    this.properties = properties;
    this.delayMillisSource = delayMillisSource;
    this.failureRollSource = failureRollSource;
  }

  @Narrated("Processing notification for order {orderId}")
  public NotificationOutcome process(String customerId, String orderId) {
    long delayMillis = delayMillisSource.getAsLong();
    sleep(delayMillis);
    failIfUnlucky(orderId);
    return new NotificationOutcome(customerId, orderId, delayMillis);
  }

  @OnError(
      value = "Simulated notification failure for order {orderId}",
      exception = NotificationFailedException.class)
  private void failIfUnlucky(String orderId) {
    if (failureRollSource.getAsInt() < properties.failurePercent()) {
      throw new NotificationFailedException("Simulated failure for order " + orderId);
    }
  }

  private void sleep(long delayMillis) {
    try {
      Thread.sleep(delayMillis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new NotificationFailedException("Interrupted while simulating delay", e);
    }
  }
}
