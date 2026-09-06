/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent.sample;

/**
 * Sample class whose methods handle exceptions internally with try/catch and try/finally.
 *
 * <p>INTENT: Instrumentation must never change the exception-handling semantics of these methods —
 * user catch blocks, finally blocks, and synchronized-block cleanup must behave exactly as in the
 * untransformed class.
 */
public class ResilientService {

  /** Set by {@link #cleanupOnFailure()}'s finally block; read back by tests via reflection. */
  public boolean cleanupRan;

  public String fetchWithFallback() {
    try {
      throw new IllegalStateException("primary source failed");
    } catch (IllegalStateException e) {
      return "fallback";
    }
  }

  public void cleanupOnFailure() {
    try {
      throw new IllegalStateException("boom");
    } finally {
      cleanupRan = true;
    }
  }

  public String rethrowUnhandledType() {
    try {
      throw new IllegalStateException("not an IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      return "never";
    }
  }

  public void syncThenThrow() {
    synchronized (this) {
      throw new IllegalStateException("locked failure");
    }
  }
}
