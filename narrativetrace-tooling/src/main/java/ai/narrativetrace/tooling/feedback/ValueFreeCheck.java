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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The hard gate: every {@link ValueFreeRule} over every field of a report, before anything is built
 * that a user could file.
 *
 * <p>INTENT: with no private inbox, a filed report is public from the first second — so the check
 * runs BEFORE the draft, the URL and the body file exist, and the verb refuses to produce any of
 * them while a violation stands. A gate that ran afterwards would be a warning, and a warning on
 * this path is a leak with a note attached.
 *
 * <p><b>@llmNote</b> Every rule is reported, not the first: a report that leaks two different
 * shapes should be fixed once, not twice. Order is field order then {@link ValueFreeRule}
 * declaration order, so the same report always refuses in the same words — which is what lets a
 * test assert on them.
 *
 * <p><b>@llmNote</b> Which rules apply is a property of the FIELD, not of the report: see {@link
 * #DOCTOR_REPORT_FIELD} for the one exemption and why it exists. {@link #rulesRefusing} is the
 * text-scoped reading with no field to exempt, and it is what the shared {@code
 * hostile-corpus/feedback.json} replay uses — so a corpus row's verdict never depends on which
 * field a value happened to arrive in.
 */
public final class ValueFreeCheck {

  /**
   * The field whose text is the doctor's OWN generated JSON, and the one field {@link
   * ValueFreeRule#MARKER} cannot read.
   *
   * <p>The doctor's check vocabulary NAMES the redaction marker: {@code trap.redaction-proof} is
   * the check "no test asserts the literal {@code "[REDACTED]"}", and both its message and its fix
   * quote that literal — whether the check passes or fails. So one rule, read against this one
   * field, refused every report from every project that has NarrativeTrace installed at all, and
   * the verb could draft nothing. Found by running the verb against a real project rather than
   * against a hand-written stand-in for one.
   *
   * <p><b>@llmNote</b> Named by a string because that is what a field map is keyed by, so this
   * constant and {@code FeedbackReport.fields()} have to agree: a renamed field silently re-arms
   * the rule. {@code ValueFreeCheckTest} asserts the report carries exactly this key.
   */
  public static final String DOCTOR_REPORT_FIELD = "doctor report";

  private ValueFreeCheck() {}

  /**
   * Which rules read {@code fieldName}.
   *
   * <p>Every rule reads every field except this one pair, and the exemption is deliberately as
   * narrow as it can be: the doctor's report is still read by the other nine, so an email, a home
   * path, a credential shape or a rendered call line inside a doctor message is refused exactly as
   * it would be anywhere else. What is exempt is one rule whose whole premise — "the marker only
   * appears where a value was blanked" — is false for OUR OWN generated text about that marker.
   */
  private static List<ValueFreeRule> rulesFor(String fieldName) {
    if (!DOCTOR_REPORT_FIELD.equals(fieldName)) {
      return ValueFreeRule.all();
    }
    return ValueFreeRule.all().stream().filter(rule -> rule != ValueFreeRule.MARKER).toList();
  }

  /** Every rule that refuses this one piece of text, in rule order; empty means it may be filed. */
  public static List<ValueFreeRule> rulesRefusing(String text) {
    if (text == null) {
      throw new IllegalArgumentException(
          "the gate reads text, never null — an absent field is \"\"");
    }
    return ValueFreeRule.all().stream().filter(rule -> rule.rejects(text)).toList();
  }

  /**
   * Every violation across a report's named fields.
   *
   * @param fields field name to its text, in the order a refusal should list them
   * @throws IllegalArgumentException when the map or any value is null — the drafter represents an
   *     absent field as {@code ""}, so a null here is a caller's bug and must not be read as empty
   */
  public static List<ValueFreeViolation> violations(Map<String, String> fields) {
    if (fields == null) {
      throw new IllegalArgumentException("the gate reads a report's fields, never null");
    }
    List<ValueFreeViolation> violations = new ArrayList<>();
    for (Map.Entry<String, String> field : fields.entrySet()) {
      if (field.getValue() == null) {
        throw new IllegalArgumentException(
            "field \"" + field.getKey() + "\" is null — an absent field is \"\"");
      }
      for (ValueFreeRule rule : rulesFor(field.getKey())) {
        if (rule.rejects(field.getValue())) {
          violations.add(new ValueFreeViolation(field.getKey(), rule));
        }
      }
    }
    return List.copyOf(violations);
  }
}
