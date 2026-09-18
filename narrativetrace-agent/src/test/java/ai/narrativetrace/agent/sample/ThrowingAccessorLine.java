/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent.sample;

/**
 * Fixture record whose one accessor always throws, for the render-reentrancy totality test: a
 * render-time guard must be released even when the woven method it invoked (this accessor) threw,
 * so the next genuine call on the same thread is still traced.
 */
public record ThrowingAccessorLine(String sku) {

  public String sku() {
    throw new IllegalStateException("boom");
  }
}
