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
package ai.narrativetrace.tooling.platforms;

import ai.narrativetrace.tooling.frameworks.FrameworkTableDocs;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The platform table, rendered for readers: the table at the top of {@code
 * documentation/use-with-your-agent.md}.
 *
 * <p>INTENT: the page's one claim that changes with the vendors — how a stranger gets the skills on
 * each platform and what the agent finds — is a view of {@link PlatformTable}, never typed. The
 * block sits between {@code <!-- platform-table:begin -->}/{@code <!-- platform-table:end -->}; the
 * build's {@code renderPlatforms} task rewrites it and {@code PlatformFilesDriftTest} fails while
 * the page disagrees. The Verified column is the honesty: no test reaches the vendors, so each row
 * carries the date its facts were last checked.
 *
 * <p><b>@llmNote</b> A cell never carries a pipe or a newline: the translations of the page mirror
 * the table's shape, and a stray pipe would add a column to the English page only.
 */
public final class PlatformTableDocs {

  /** The page this table is rendered into, repository-relative. */
  public static final String PAGE = "documentation/use-with-your-agent.md";

  static final String BLOCK = "platform-table";

  private PlatformTableDocs() {}

  /** The Markdown table: a header, a separator, and one line per platform in table order. */
  static String table() {
    StringBuilder out = new StringBuilder();
    out.append("| Platform | How a stranger gets the skills | What the agent finds after `init` |")
        .append(" Evidence | Verified |\n")
        .append("|---|---|---|---|---|\n");
    PlatformTable.ROWS.forEach(row -> out.append(line(row)));
    return out.toString();
  }

  /** The page with its table block re-rendered. */
  public static String splice(String page) {
    return FrameworkTableDocs.splice(page, BLOCK, table());
  }

  private static String line(PlatformRow row) {
    return "| "
        + Stream.of(row.name(), row.installPath(), row.agentFinds(), evidence(row), row.verified())
            .map(PlatformTableDocs::cell)
            .collect(Collectors.joining(" | "))
        + " |\n";
  }

  private static String evidence(PlatformRow row) {
    return PlatformRow.MANUAL.equals(row.evidenceLane())
        ? "manual trial"
        : "Tier B lane `" + row.evidenceLane() + "`";
  }

  private static String cell(String text) {
    return text.replace("|", "\\|").replace("\n", " ");
  }
}
