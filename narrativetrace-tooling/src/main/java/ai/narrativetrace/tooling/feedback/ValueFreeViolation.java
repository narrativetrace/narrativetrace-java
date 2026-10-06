/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.tooling.feedback;

/**
 * One refusal: the report field that carried the offending text, and the rule that refused it.
 *
 * <p>INTENT: a refusal has to be actionable. "This report cannot be filed" sends a person looking
 * through every field; "{@code happened} breaks {@code vf.rendered-call}" sends them to one line.
 *
 * @param field the report field's own name, as the draft prints it
 * @param rule the rule that refused it
 */
public record ValueFreeViolation(String field, ValueFreeRule rule) {

  public ValueFreeViolation {
    if (field == null || field.isBlank()) {
      throw new IllegalArgumentException("a violation names the field it was found in");
    }
    if (rule == null) {
      throw new IllegalArgumentException("a violation names the rule that refused it");
    }
  }

  /** The one line a refusal prints: the field, the rule id, and what to do about it. */
  public String describe() {
    return field + ": " + rule.id() + " — " + rule.reason();
  }
}
