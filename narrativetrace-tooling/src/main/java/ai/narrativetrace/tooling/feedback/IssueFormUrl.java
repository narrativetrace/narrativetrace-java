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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The pre-filled issue-form URL — the default way a report is filed, and the only one that needs no
 * tool installed and no credential held by anybody but the person clicking it.
 *
 * <p>INTENT: the user opens this in their own browser, where they are already signed in, and
 * submits it themselves. That click is the approval and the authentication at once, which is why
 * there is no endpoint, no token and nothing for an agent to hold. The agent prints the URL; it
 * never opens it.
 *
 * <p><b>@llmNote</b> The BODY is deliberately not in the URL. A doctor report and a structural
 * trace are kilobytes, a URL that is too long answers 414, and the limit is undocumented — so the
 * long fields live in {@code feedback-body.md} and the user pastes them. What travels in the URL is
 * the short, searchable half: the category, the install coordinate, the step, the language and the
 * agent line.
 *
 * <p><b>@llmNote</b> {@code labels} only takes effect for someone with push access ("labels are
 * silently dropped otherwise"), so for an outside reporter the parameter is harmless and inert; it
 * is here because the same label set is what the {@code gh} path applies, and two label lists that
 * could disagree would be two triage queues.
 */
public final class IssueFormUrl {

  /**
   * The length this URL is kept under.
   *
   * <p>GitHub answers {@code 414 URI Too Long} past an undocumented limit, so the budget is a
   * conservative one we set ourselves and measure, rather than one we discover from a user's
   * failure. {@link #staysUnderBudget} is the postcondition; {@code IssueFormUrlTest} is the proof.
   */
  public static final int MAX_LENGTH = 4_000;

  /** What a field that did not fit ends with, so a reader knows the rest is in the body file. */
  public static final String TRUNCATION_MARKER = " … (continued in the pasted body)";

  private static final String BASE = "https://github.com/" + PublicRepository.SLUG + "/issues/new";

  /** Per-field ceiling before the whole-URL budget is enforced. */
  private static final int FIELD_LIMIT = 400;

  private IssueFormUrl() {}

  /**
   * The URL for this report.
   *
   * @throws IllegalArgumentException when the report is not a Java report
   */
  public static String of(FeedbackReport report) {
    PublicRepository.requireJavaRuntime(report);
    String url = build(report, FIELD_LIMIT);
    for (int limit = FIELD_LIMIT; url.length() > MAX_LENGTH && limit > 16; limit /= 2) {
      url = build(report, limit);
    }
    assert staysUnderBudget(url) : "the issue-form URL must stay inside its own budget";
    return url;
  }

  /** Whether a built URL is inside the budget — the postcondition {@link #of} asserts. */
  static boolean staysUnderBudget(String url) {
    return url.length() <= MAX_LENGTH;
  }

  private static String build(FeedbackReport report, int fieldLimit) {
    Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put("template", PublicRepository.FORM);
    parameters.put("title", clip(PublicRepository.titleFor(report), fieldLimit));
    parameters.put("labels", String.join(",", PublicRepository.labelsFor(report)));
    parameters.put("runtime", clip(report.runtime(), fieldLimit));
    parameters.put("category", report.category().id());
    parameters.put("install", clip(report.install(), fieldLimit));
    parameters.put("step", clip(report.step(), fieldLimit));
    parameters.put("language", clip(report.language(), fieldLimit));
    parameters.put("agent", clip(report.agent().describe(), fieldLimit));
    return BASE + "?" + query(parameters);
  }

  private static String query(Map<String, String> parameters) {
    StringBuilder out = new StringBuilder();
    for (Map.Entry<String, String> parameter : parameters.entrySet()) {
      if (out.length() > 0) {
        out.append('&');
      }
      out.append(parameter.getKey()).append('=').append(encode(parameter.getValue()));
    }
    return out.toString();
  }

  /**
   * Percent-encoding, with the space written {@code %20} rather than {@code +}.
   *
   * <p>{@code URLEncoder} implements HTML form encoding, where a space is {@code +}; that is right
   * inside a form POST and wrong inside a query a browser hands to GitHub's prefill reader, which
   * would show the plus signs.
   */
  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static String clip(String value, int limit) {
    return value.length() <= limit ? value : value.substring(0, limit) + TRUNCATION_MARKER;
  }
}
