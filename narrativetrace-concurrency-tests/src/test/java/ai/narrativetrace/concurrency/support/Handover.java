/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.support;

/** The hand-over mechanisms of the matrix. */
public enum Handover {
  /** {@code ForkGroup}: wrap each task, join, merge under the submitting span. */
  FORK_GROUP("reserve"),
  /**
   * {@code FireAndForgetGroup}: a launcher node in the request; the work is read from the group.
   */
  FIRE_AND_FORGET("reserve"),
  /** An executor whose tasks the Spring {@code ContextPropagatingTaskDecorator} decorates. */
  DECORATED_EXECUTOR("reserve"),
  /** Spring {@code @Async} on a bean method. */
  ASYNC("reserveAsync");

  private final String workMethod;

  Handover(String workMethod) {
    this.workMethod = workMethod;
  }

  /** The traced method the workers run. */
  public String workMethod() {
    return workMethod;
  }
}
