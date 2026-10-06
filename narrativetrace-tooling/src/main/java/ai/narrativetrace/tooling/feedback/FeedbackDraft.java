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

import java.util.List;

/**
 * What drafting produced: either a report the gate cleared, with the two texts rendered from it, or
 * a refusal naming every rule that stood in the way.
 *
 * <p>INTENT: a sealed pair rather than a draft carrying a list of problems, so no caller can print
 * a URL or write a body file while a violation stands. The compiler is what enforces the gate's
 * ordering here — a {@code Refused} has no body to file, by construction.
 */
public sealed interface FeedbackDraft {

  /**
   * The gate refused. Nothing was rendered and nothing may be written.
   *
   * @param violations every rule that refused, field order then rule order; never empty
   */
  record Refused(List<ValueFreeViolation> violations) implements FeedbackDraft {

    public Refused {
      violations = List.copyOf(violations);
      if (violations.isEmpty()) {
        throw new IllegalArgumentException(
            "a refusal names what refused it — an empty violation list is a Drafted");
      }
    }

    /** The refusal, one line per violation, in the words the verb prints before exiting 2. */
    public String describe() {
      StringBuilder out = new StringBuilder();
      out.append("This report cannot be filed. ")
          .append(violations.size())
          .append(" rule(s) refused it:\n");
      for (ValueFreeViolation violation : violations) {
        out.append("  - ").append(violation.describe()).append('\n');
      }
      return out.toString();
    }
  }

  /**
   * The gate cleared the report.
   *
   * @param report the report AFTER home-path rewriting — what the two texts were rendered from
   * @param draft the whole report plus the privacy note and the two choices: what the agent SHOWS
   * @param body the report alone, in Markdown: what gets filed, with nothing about the process
   */
  record Drafted(FeedbackReport report, String draft, String body) implements FeedbackDraft {

    public Drafted {
      if (report == null || draft == null || body == null) {
        throw new IllegalArgumentException("a drafted report carries its report and both texts");
      }
      if (!draft.contains(body)) {
        throw new IllegalArgumentException(
            "the draft must contain the body verbatim — a person who approves the draft is"
                + " approving what gets filed, byte for byte");
      }
    }
  }
}
