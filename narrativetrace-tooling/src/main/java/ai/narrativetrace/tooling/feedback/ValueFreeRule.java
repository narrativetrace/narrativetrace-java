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
import java.util.function.Predicate;

/**
 * The hard gate between a problem report and a leaked secret: one named rule per shape of runtime
 * value that must never reach a public issue.
 *
 * <p>INTENT: a report drafted by an agent is PUBLIC from the first second — there is no private
 * inbox to triage it first — so the only thing standing between a pasted trace and somebody's
 * credential is a check the verb runs before it will build a URL or a body file. Each rule carries
 * a stable {@code vf.*} id because the refusal has to name what to fix, not just say no.
 *
 * <p><b>@llmNote</b> Data here, code in {@link ValueFreeMatchers}: a constant is an id, a reason a
 * person reads, and a reference to the matcher that decides. Adding a rule means adding a constant
 * and a matcher, never an {@code if} inside a caller — {@link ValueFreeCheck} walks {@link
 * #values()} and cannot know about a rule that is not here.
 *
 * <p><b>@llmNote</b> These rules are deliberately STRICTER than the renderer's own redaction
 * ({@code RedactionPolicy} in the runtime this library never links against). The renderer refuses
 * an entropy heuristic because a false positive there silently blanks a user's data; here a false
 * positive is a refusal that names its rule and a false negative is a public leak, so the tradeoff
 * inverts. The cross-module security suite asserts the implication that matters — every value the
 * renderer redacts is also rejected here — and asserts it in that direction ONLY.
 */
public enum ValueFreeRule {

  /** A rendered call line: {@code method(param: value)} carries the argument's value. */
  RENDERED_CALL(
      "vf.rendered-call",
      "a call line carries a parameter's VALUE — attach the structural trace (.nt) instead, which"
          + " carries the same shape without any value",
      ValueFreeMatchers::renderedCall),

  /** A rendered outcome: an arrow followed by anything but the structural literal {@code value}. */
  RENDERED_OUTCOME(
      "vf.rendered-outcome",
      "an outcome arrow carries a RETURNED VALUE — the structural trace writes a bare"
          + " \u2192 value, an exception TYPE after !!, or ?? incomplete, and never a value",
      ValueFreeMatchers::renderedOutcome),

  /** A rendered duration: the elapsed time a value-free artifact never carries. */
  DURATION(
      "vf.duration",
      "an elapsed time is a runtime measurement, which means this text came from a rendered"
          + " narrative rather than from the structural trace",
      ValueFreeMatchers::duration),

  /** The renderer's redaction marker, which no value-free artifact ever contains. */
  MARKER(
      "vf.marker",
      "the redaction marker only appears where a value was redacted, so this text is a rendered"
          + " narrative — the structural trace has nothing to redact and never carries it",
      ValueFreeMatchers::marker),

  /** A deny-listed field name with a value assigned to it. */
  NAMED_SECRET(
      "vf.named-secret",
      "a field whose NAME says it holds a credential is shown with a value beside it \u2014 remove"
          + " the value; the name alone is shape and may stay",
      ValueFreeMatchers::namedSecret),

  /** A value whose SHAPE is the secret: a credential prefix, a key block, a national id, a card. */
  VALUE_SHAPE(
      "vf.value-shape",
      "a value here is shaped like a credential, a key, a national id or a card number \u2014"
          + " remove it; a report never needs the value, only what happened",
      ValueFreeMatchers::valueShape),

  /** A run of encoded characters dense enough to be a key rather than a word. */
  ENTROPY(
      "vf.entropy",
      "a long encoded run here looks like a key, a hash or an opaque identifier \u2014 remove it;"
          + " if it is genuinely not a secret, describe it in words instead of pasting it",
      ValueFreeMatchers::entropy),

  /** An email address \u2014 somebody's personal data, whoever's it is. */
  EMAIL(
      "vf.email",
      "an email address is personal data and a public issue is public forever \u2014 remove it;"
          + " the report does not need to say who",
      ValueFreeMatchers::email),

  /** An absolute home directory, which carries whoever's login name. */
  HOME_PATH(
      "vf.home-path",
      "an absolute home directory names the account it belongs to \u2014 the draft rewrites these"
          + " to ~ on its own, so one here means the text was edited by hand afterwards",
      ValueFreeMatchers::homePath),

  /** A control character: never content, always a terminal escape or a smuggled byte. */
  CONTROL(
      "vf.control",
      "a control character is not text a reader needs and is how a payload hides in one \u2014"
          + " only a line feed and a tab belong in a report",
      ValueFreeMatchers::control);

  private final String id;
  private final String reason;
  private final Predicate<String> matcher;

  ValueFreeRule(String id, String reason, Predicate<String> matcher) {
    this.id = id;
    this.reason = reason;
    this.matcher = matcher;
  }

  /** The stable rule id a refusal names, e.g. {@code vf.rendered-call}. */
  public String id() {
    return id;
  }

  /** Why this shape may not be filed, in the words the refusal prints. */
  public String reason() {
    return reason;
  }

  /**
   * Whether this rule refuses the given text.
   *
   * @throws IllegalArgumentException when {@code text} is null — an absent field is {@code ""} to
   *     every caller in this package, so a null here is a programming error, not empty input
   */
  public boolean rejects(String text) {
    if (text == null) {
      throw new IllegalArgumentException("a value-free rule reads text, never null");
    }
    return matcher.test(text);
  }

  /** Every rule, in declaration order — the order a refusal lists them in. */
  public static List<ValueFreeRule> all() {
    return List.of(values());
  }
}
