/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.shop;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;

/** The {@code @Async} worker-side service of the Spring cells. */
public interface AsyncInventory {

  @Async
  CompletableFuture<String> reserveAsync(String ref);
}
