/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills;

/**
 * An "always"/"never" rule with the reason it exists — every such rule carries one (Tier A lint).
 */
public record ReasonedRule(String rule, String reason) {

  public ReasonedRule {
    if (rule == null || rule.isBlank()) {
      throw new IllegalArgumentException("a ReasonedRule's rule must not be blank");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("a ReasonedRule's reason must not be blank");
    }
  }
}
