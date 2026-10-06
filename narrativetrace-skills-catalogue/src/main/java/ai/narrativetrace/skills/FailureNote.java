/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills;

/** One documented symptom → cause → fix triple attached to a step. */
public record FailureNote(String symptom, String cause, String fix) {

  public FailureNote {
    requireText(symptom, "symptom");
    requireText(cause, "cause");
    requireText(fix, "fix");
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("a FailureNote's " + field + " must not be blank");
    }
  }
}
