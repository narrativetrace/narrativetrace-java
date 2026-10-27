/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.shop;

import java.util.concurrent.CompletableFuture;

/** Runs on the {@code @Async} executor's thread. */
public class InMemoryAsyncInventory implements AsyncInventory {

  @Override
  public CompletableFuture<String> reserveAsync(String ref) {
    return CompletableFuture.completedFuture(
        "reserved async " + ref + " on " + Thread.currentThread().getName());
  }
}
