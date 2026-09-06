/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

/**
 * Scope returned by {@link ContextSnapshot#activate()}.
 *
 * <p>INTENT: Use this with try-with-resources so the previous thread-local trace state is restored
 * even when the wrapped task throws.
 *
 * @see ContextSnapshot
 */
public interface ContextScope extends AutoCloseable {
  /** Restores the previous context. Does not throw checked exceptions. */
  @Override
  void close();
}
