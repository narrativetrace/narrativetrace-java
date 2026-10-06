/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

/**
 * One row of {@code feedback.json}: problem-report text, and which way the value-free gate must
 * decide about it.
 *
 * <p>{@code rule} is the rule id whose presence a {@code rejected} row asserts, and is {@code null}
 * on an {@code accepted} row. A rejected row asserts CONTAINMENT rather than equality: a leak has a
 * shape, not an id, and a pasted rendered line legitimately breaks the call rule and the duration
 * rule at once.
 *
 * @param expect {@code "rejected"} or {@code "accepted"}
 */
public record FeedbackCase(
    String id, String description, String value, String expect, String rule) {

  public boolean mustBeRejected() {
    return "rejected".equals(expect);
  }
}
