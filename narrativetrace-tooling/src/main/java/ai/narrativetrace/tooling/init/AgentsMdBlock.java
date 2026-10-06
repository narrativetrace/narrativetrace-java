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
package ai.narrativetrace.tooling.init;

/**
 * Renders the managed section the installer writes into a consumer's {@code AGENTS.md}.
 *
 * <p>INTENT: the always-on pointer. An agent that never saw an install prompt reads this section at
 * the start of its next session, finds the skills by name, and knows which command diagnoses the
 * project — the discovery channel that costs no description budget and needs no tool.
 *
 * <p>What goes in, in this order: one line on what NarrativeTrace is here and where traces land,
 * the skills with the catalogue's own descriptions, the commands, the documentation pointer, and
 * the three rules the evaluations keep tripping over.
 *
 * <p><b>@llmNote</b> Nothing project-specific goes in beyond what the snapshot DETECTED — the
 * output directory and whether this is a Gradle build. No inference, no generated coding rules, and
 * no version talk: the coordinate on the opening marker is a machine-written stamp, and it is the
 * only version this block ever carries.
 *
 * <p><b>@llmNote</b> The Gradle preview command is {@code narrativetraceInit --diff}, NOT {@code
 * --dry-run}: Gradle owns {@code --dry-run} as a built-in that skips every task in the graph, so a
 * section naming it would send every reader to a command that prints nothing. The command-line verb
 * keeps {@code init --dry-run}, where nothing shadows it. The two spellings are deliberate and the
 * tests pin both.
 *
 * <p><b>@llmNote</b> Renders with {@code \n} throughout. A caller writing into a file that uses
 * another line ending converts with {@link MarkedBlock#withEol}.
 */
final class AgentsMdBlock {

  /** The runtime's documentation index, the one link an agent needs from here. */
  static final String DOCS_URL = "https://narrativetrace.ai/java/llms.txt";

  private AgentsMdBlock() {}

  /** The whole section, opening marker through closing marker, ending with a newline. */
  static String render(Carrier carrier, ProjectState state) {
    if (carrier == null || state == null) {
      throw new IllegalArgumentException("a carrier and a project state are needed to render");
    }
    StringBuilder out = new StringBuilder();
    out.append(MarkedBlock.START).append(' ').append(carrier.coordinate()).append(" -->\n");
    appendIntroduction(out, state);
    appendSkills(out, carrier);
    appendCommands(out, state);
    appendRules(out);
    out.append(MarkedBlock.END).append('\n');
    assert MarkedBlock.scan(out.toString()).hasExactlyOneRegion() : "the block must be one region";
    return out.toString();
  }

  private static void appendIntroduction(StringBuilder out, ProjectState state) {
    out.append("## NarrativeTrace\n\n")
        .append("NarrativeTrace turns this project's own method names, parameters and return")
        .append(" values into a readable execution narrative — no log statements. Rendered")
        .append(" traces land in `")
        .append(state.outputDirectory())
        .append("`.\n\n");
  }

  private static void appendSkills(StringBuilder out, Carrier carrier) {
    out.append("### Agent skills installed in this project\n\n");
    for (SkillEntry skill : carrier.skills()) {
      out.append("- `")
          .append(skill.name())
          .append("` — ")
          .append(skill.description())
          .append('\n');
    }
    out.append('\n');
  }

  private static void appendCommands(StringBuilder out, ProjectState state) {
    boolean gradle = state.gradleProject();
    out.append("### Commands\n\n")
        .append("- `")
        .append(gradle ? "./gradlew narrativetraceDoctor" : "narrativetrace doctor")
        .append("` — diagnose this install; read-only, and every finding names the skill that")
        .append(" fixes it\n")
        .append("- `")
        .append(gradle ? "./gradlew narrativetraceInit --diff" : "narrativetrace init --dry-run")
        .append("` — show what re-installing the skills would change, as a diff\n")
        .append("- `")
        .append(gradle ? "./gradlew narrativetraceUninstall" : "narrativetrace uninstall")
        .append("` — remove exactly what the installer wrote, this section included\n\n")
        .append("Documentation: ")
        .append(DOCS_URL)
        .append("\n\n");
  }

  private static void appendRules(StringBuilder out) {
    out.append("### Rules\n\n")
        .append("- Keep the `-parameters` compiler flag. Without it the trace reads `arg0`,")
        .append(" `arg1`, and the narrative is gone.\n")
        .append("- Never disable redaction to make a trace easier to read.\n")
        .append("- Commit `.approved.nt` files; never commit a `.received.nt`.\n\n");
  }
}
