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
 * The three sentences that are the report: what was done, what happened, what was expected.
 *
 * <p>INTENT: all three are mandatory, and that is the whole design of the field set. "It does not
 * work" is not a report; the difference between what happened and what was expected is what makes
 * one triageable without a conversation, and a conversation is exactly what nobody gets when the
 * reporter is an agent that has already ended its session.
 */
public record ProblemNarrative(String did, String happened, String expected) {

  public ProblemNarrative {
    require(did, "did");
    require(happened, "happened");
    require(expected, "expected");
  }

  private static void require(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(
          "a report's \"" + field + "\" must say something — all three sentences are mandatory");
    }
  }
}
