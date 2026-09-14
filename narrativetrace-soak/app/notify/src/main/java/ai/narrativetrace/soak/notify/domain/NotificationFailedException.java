/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.notify.domain;

/** Thrown by {@link NotificationProcessor} for the configured share of simulated failures. */
public class NotificationFailedException extends RuntimeException {

  public NotificationFailedException(String message) {
    super(message);
  }

  public NotificationFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
