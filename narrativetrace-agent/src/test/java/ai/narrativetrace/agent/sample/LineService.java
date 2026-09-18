/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent.sample;

/**
 * Fixture service used by the render-reentrancy tests. One method never touches its {@link
 * CartLine} parameter's accessors (isolating parameter-render reentrancy); one calls an accessor
 * itself (isolating that genuine, application-code accessor calls are still traced); one returns a
 * freshly built {@link CartLine} without reading it back (isolating return-value-render
 * reentrancy).
 */
public class LineService {

  /** Never touches {@code line}'s accessors — any accessor span seen here comes from rendering. */
  public String receive(CartLine line) {
    return "received";
  }

  /** Calls {@code line.quantity()} itself — that call must remain traced as a genuine child. */
  public int quantityOf(CartLine line) {
    return line.quantity();
  }

  /** Takes no record parameter, so only the returned value's rendering can produce spans. */
  public CartLine makeLine() {
    return new CartLine("SKU-MECHANICAL-KB", 2);
  }

  /** Never touches {@code line}'s accessors; used with {@link PausableLine}'s blocking accessor. */
  public String receivePausable(PausableLine line) {
    return "received";
  }

  /** Never touches {@code line}'s accessor; used with {@link ThrowingAccessorLine}. */
  public String receiveThrowing(ThrowingAccessorLine line) {
    return "received";
  }
}
