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

import java.util.List;
import java.util.Optional;

/**
 * The platform table — Phase 8's D1: ONE committed registry of the agent platforms NarrativeTrace
 * is used from, how a project proves it is on one, which pointer {@code init} writes for it, how a
 * stranger installs the skills, and which evidence lane exercises it.
 *
 * <p>INTENT: the framework table's sibling, same shape. {@code init}'s plan, {@code uninstall}, the
 * doctor's {@code config.skills-installed}, the Cursor plugin manifest and {@code
 * documentation/use-with-your-agent.md} all read these rows, and none of them is typed twice.
 *
 * <p><b>@llmNote</b> Every platform here already discovers the pages {@code init} writes under
 * {@code .agents/skills} (and {@code .claude/skills}); a row never adds a third page flavour. Row
 * ids are a cross-port contract — append, never rename. The platform facts carry their verification
 * date because a docs-as-tests check cannot reach the vendors: re-verify them at each release and
 * move the date.
 */
public final class PlatformTable {

  /** The date every fact below was last checked against the vendors' own documentation. */
  private static final String VERIFIED = "2026-10-10";

  private static final String INIT = "run `narrativetrace init` in the project";

  public static final List<PlatformRow> ROWS =
      List.of(cursor(), replit(), antigravity(), githubCopilot(), claudeCode());

  private PlatformTable() {}

  /** The row with this id, if the table has one. */
  public static Optional<PlatformRow> row(String id) {
    return ROWS.stream().filter(r -> r.id().equals(id)).findFirst();
  }

  private static PlatformRow cursor() {
    return new PlatformRow(
        "cursor",
        "Cursor",
        List.of(".cursor"),
        new Pointer.Rule(
            ".cursor/rules/narrativetrace.mdc",
            "The NarrativeTrace skills live under `.agents/skills` — read them before changing"
                + " traced code, and run the NarrativeTrace doctor to check the install."),
        "Install the plugin from the public repository's `.cursor-plugin` manifest, or " + INIT,
        "the skills in `.agents/skills` (Cursor also reads `.claude/skills`), and the one rule"
            + " `.cursor/rules/narrativetrace.mdc` that points at them",
        "cursor",
        VERIFIED);
  }

  private static PlatformRow replit() {
    return new PlatformRow(
        "replit",
        "Replit",
        List.of("replit.md", ".replit"),
        new Pointer.ManagedSection("replit.md"),
        "Import the public repository from GitHub (paste its URL, preview the skills, add them),"
            + " or "
            + INIT,
        "the skills in `.agents/skills`, and a NarrativeTrace section in `replit.md`, the"
            + " project memory the Replit agent reads first",
        PlatformRow.MANUAL,
        VERIFIED);
  }

  /**
   * Antigravity (Google's agentic IDE and its {@code agy} CLI). Its markers are its OWN project
   * layout — {@code .agents/rules} and {@code .agents/workflows}, legacy {@code .agent/} — never
   * {@code .agents/skills}, which {@code init} itself writes. No registry install path: {@code agy
   * plugin install} reads only a plugin's top-level {@code skills/} directory ({@code agy plugin
   * validate} 1.3.3 reports a {@code .agents/skills} layout as "skipped (not found)"), and this
   * repository ships none.
   */
  private static PlatformRow antigravity() {
    return new PlatformRow(
        "antigravity",
        "Antigravity",
        List.of(".agents/rules", ".agents/workflows", ".agent"),
        new Pointer.None(),
        "Nothing to install beyond the project itself: "
            + INIT
            + " and commit the skills (the `agy` CLI and the IDE read them as workspace skills)",
        "the skills in `.agents/skills`, which Antigravity reads as workspace skills, and the"
            + " section in `AGENTS.md`, which it loads as rules",
        "antigravity",
        VERIFIED);
  }

  private static PlatformRow githubCopilot() {
    return new PlatformRow(
        "github-copilot",
        "GitHub Copilot",
        List.of(".github/copilot-instructions.md"),
        new Pointer.None(),
        "Nothing to install beyond the repository itself: " + INIT + " and commit the skills",
        "the skills in `.agents/skills` (Copilot also reads `.claude/skills` and"
            + " `.github/skills`), and the section in `AGENTS.md`",
        PlatformRow.MANUAL,
        VERIFIED);
  }

  private static PlatformRow claudeCode() {
    return new PlatformRow(
        "claude-code",
        "Claude Code",
        List.of(".claude"),
        new Pointer.None(),
        "Add the repository as a plugin marketplace and install its plugin, or " + INIT,
        "the skills in `.claude/skills` and `.agents/skills`, and the section in `AGENTS.md`"
            + " that `CLAUDE.md` imports",
        "claude",
        VERIFIED);
  }
}
