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
package ai.narrativetrace.tooling.frameworks;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The framework table, rendered for readers: the integration table at the top of {@code
 * llms-full.md}'s "Integration Guides" and the covered-frameworks line in {@code llms.txt}.
 *
 * <p>INTENT: the doctor, the docs and the skill all answer "which frameworks, which module, which
 * lines" — so all three read {@link FrameworkTable}, and none is typed. Each rendered block sits
 * between {@code <!-- <name>:begin -->}/{@code <!-- <name>:end -->} markers; the build's {@code
 * renderFrameworkTable} task rewrites them and a drift test fails while they disagree. The wiring
 * snippets inside the integration block carry the same {@code snippet:} markers as the resource, so
 * {@code snippetCheck} holds the page to the compiled fixtures as well.
 */
public final class FrameworkTableDocs {

  /** The complete reference, repository-relative. */
  public static final String LLMS_FULL = "documentation/llms-full.md";

  /** The agent entry point, repository-relative. */
  public static final String LLMS_TXT = "documentation/llms.txt";

  static final String INTEGRATION_BLOCK = "framework-table";
  static final String COVERED_BLOCK = "covered-frameworks";

  private FrameworkTableDocs() {}

  /** {@code llms-full.md} with its integration block re-rendered for {@code version}. */
  public static String renderLlmsFull(String document, String version) {
    return splice(document, INTEGRATION_BLOCK, llmsFullSection(version));
  }

  /** {@code llms.txt} with its covered-frameworks line re-rendered. */
  public static String renderLlmsTxt(String document) {
    return splice(document, COVERED_BLOCK, llmsTxtLine() + "\n");
  }

  /** The integration table and every source-wired row's snippet, for {@code llms-full.md}. */
  static String llmsFullSection(String version) {
    StringBuilder out = new StringBuilder();
    out.append("### Framework table — what the doctor checks\n\n")
        .append("Rendered from the doctor's own framework table. For every row it can observe,")
        .append(" `narrativetraceDoctor` runs the named check: the framework is detected but its")
        .append(" module is not referenced, or the module is referenced but its wiring was never")
        .append(" applied — and the failing fix prints the lines below.\n\n")
        .append("| Framework | Detected by | Add (Gradle plugin) | Add (plain Gradle) | Wiring |")
        .append(" Doctor check |\n")
        .append("|---|---|---|---|---|---|\n");
    FrameworkTable.ROWS.forEach(row -> out.append(tableRow(row, version)));
    FrameworkTable.ROWS.stream()
        .filter(row -> row.wiring() instanceof Wiring.Snippet)
        .forEach(row -> out.append(snippetSection(row)));
    return out.toString();
  }

  /** One line naming every row, in table order, for {@code llms.txt}. */
  static String llmsTxtLine() {
    return "Covered frameworks (each row of the doctor's framework table): "
        + FrameworkTable.ROWS.stream()
            .map(FrameworkTableDocs::coverage)
            .collect(Collectors.joining("; "))
        + ".";
  }

  /**
   * {@code document} with everything between the {@code name} markers replaced by {@code content}.
   *
   * @throws IllegalArgumentException when the document lacks either marker
   */
  static String splice(String document, String name, String content) {
    String begin = "<!-- " + name + ":begin -->\n";
    String end = "<!-- " + name + ":end -->";
    int from = document.indexOf(begin);
    int to = document.indexOf(end);
    if (from < 0 || to < from) {
      throw new IllegalArgumentException(
          "the document has no " + name + ":begin / " + name + ":end marker pair");
    }
    return document.substring(0, from + begin.length()) + content + document.substring(to);
  }

  private static String tableRow(FrameworkRow row, String version) {
    IntegrationModule module = row.module();
    String plugin = module.plugin() == null ? "—" : code(module.pluginLine());
    String plain =
        module.dependencyLines(version).stream()
            .map(FrameworkTableDocs::code)
            .collect(Collectors.joining("<br>"));
    return "| "
        + String.join(
            " | ",
            row.name(),
            row.marker().description(),
            plugin,
            plain,
            row.wiring().description(),
            check(row.check()))
        + " |\n";
  }

  private static String snippetSection(FrameworkRow row) {
    Wiring.Snippet snippet = (Wiring.Snippet) row.wiring();
    WiringSnippets.Entry entry = WiringSnippets.entry(row.id());
    return "\n#### "
        + row.name()
        + " — wiring\n\n<!-- snippet: "
        + entry.fixture()
        + (entry.region() == null ? "" : " region=" + entry.region())
        + " -->\n```"
        + snippet.language()
        + "\n"
        + entry.body()
        + "```\n<!-- /snippet -->\n";
  }

  private static String check(CheckBinding binding) {
    String existing = binding instanceof CheckBinding.ExistingCheck ? " (existing)" : "";
    return checkId(binding).map(id -> code(id) + existing).orElse("none — " + reason(binding));
  }

  private static String coverage(FrameworkRow row) {
    CheckBinding binding = row.check();
    return row.name()
        + " ("
        + checkId(binding).map(FrameworkTableDocs::code).orElse(reason(binding))
        + ")";
  }

  /** The id of the check that observes a row, or empty for a row no check can observe. */
  private static Optional<String> checkId(CheckBinding binding) {
    if (binding instanceof CheckBinding.WiringCheck c) {
      return Optional.of(c.id());
    }
    if (binding instanceof CheckBinding.ExistingCheck c) {
      return Optional.of(c.id());
    }
    return Optional.empty();
  }

  private static String reason(CheckBinding binding) {
    return binding instanceof CheckBinding.NoCheck c ? c.reason() : "";
  }

  private static String code(String text) {
    return "`" + text + "`";
  }
}
