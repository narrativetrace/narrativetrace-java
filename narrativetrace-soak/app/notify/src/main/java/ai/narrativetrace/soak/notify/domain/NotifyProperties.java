/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.notify.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable shape of the notify process's simulated third-party call.
 *
 * @param minDelayMillis lower bound of the simulated processing sleep (inclusive)
 * @param maxDelayMillis upper bound of the simulated processing sleep (inclusive)
 * @param failurePercent percent (0-100) of requests answered with a 503 instead of success
 */
@ConfigurationProperties(prefix = "soak.notify")
public record NotifyProperties(long minDelayMillis, long maxDelayMillis, int failurePercent) {

  private static final long DEFAULT_MIN_DELAY_MILLIS = 5;
  private static final long DEFAULT_MAX_DELAY_MILLIS = 50;
  private static final int DEFAULT_FAILURE_PERCENT = 1;

  /** Binds missing YAML/env keys to the documented defaults (5-50ms, 1% failure). */
  public NotifyProperties {
    if (minDelayMillis <= 0) {
      minDelayMillis = DEFAULT_MIN_DELAY_MILLIS;
    }
    if (maxDelayMillis <= 0) {
      maxDelayMillis = DEFAULT_MAX_DELAY_MILLIS;
    }
    if (failurePercent < 0) {
      failurePercent = DEFAULT_FAILURE_PERCENT;
    }
    if (maxDelayMillis < minDelayMillis) {
      throw new IllegalArgumentException(
          "soak.notify.max-delay-millis must be >= min-delay-millis");
    }
    if (failurePercent > 100) {
      throw new IllegalArgumentException("soak.notify.failure-percent must be <= 100");
    }
  }
}
