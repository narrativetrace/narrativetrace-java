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
 * Where a Java problem report is filed, and the one form it is filed through.
 *
 * <p>INTENT: one place, so the issue-form URL and the {@code gh} line cannot name different
 * repositories or different templates. Every runtime in the family has its own repository and the
 * SAME form file name — the form is a cross-runtime artifact, the repository is not.
 *
 * <p><b>@llmNote</b> {@link #requireJavaRuntime} exists because a wrong answer here is unfixable in
 * public: a report filed into this tracker from another runtime's project is a public issue in the
 * wrong repository, and deleting it does not un-publish it. The report's own runtime field is
 * checked rather than assumed.
 */
public final class PublicRepository {

  /** The public Java repository, {@code owner/name}. */
  public static final String SLUG = "narrativetrace/narrativetrace-java";

  /** The issue form every runtime's repository carries under {@code .github/ISSUE_TEMPLATE/}. */
  public static final String FORM = "narrativetrace-report.yml";

  /** The runtime this library files for. */
  public static final String RUNTIME = "java";

  private PublicRepository() {}

  /**
   * @throws IllegalArgumentException when the report is not a Java report
   */
  static void requireJavaRuntime(FeedbackReport report) {
    if (!RUNTIME.equals(report.runtime())) {
      throw new IllegalArgumentException(
          "this library files into "
              + SLUG
              + ", and the report's runtime is \""
              + report.runtime()
              + "\" — file it through that runtime's own tooling");
    }
  }

  /**
   * The longest a GitHub label name may be. A label built past this is one GitHub will refuse, so
   * building it is never the right answer — and an unbounded label is also how a 10,000-character
   * language tag got into a URL that then blew its own length budget.
   */
  private static final int LABEL_LIMIT = 50;

  /**
   * The labels triage sorts on, in a stable order, each inside the platform's own length limit.
   *
   * <p><b>@llmNote</b> No {@code agent:<product>} label, deliberately. The agent product is free
   * text as the agent reported it, so a label built from it is a label set strangers extend — and
   * labels from a reporter without push access are silently dropped anyway. The agent line travels
   * as a FIELD, where it is searchable and harmless.
   */
  static java.util.List<String> labelsFor(FeedbackReport report) {
    return java.util.stream.Stream.of(
            "from-agent",
            "runtime:" + report.runtime(),
            "category:" + report.category().id(),
            "lang:" + report.language())
        .map(PublicRepository::clipLabel)
        .toList();
  }

  private static String clipLabel(String label) {
    return label.length() <= LABEL_LIMIT ? label : label.substring(0, LABEL_LIMIT);
  }

  /** The issue title: the category, then the step it happened at. */
  static String titleFor(FeedbackReport report) {
    return report.category().id() + ": " + report.step();
  }
}
