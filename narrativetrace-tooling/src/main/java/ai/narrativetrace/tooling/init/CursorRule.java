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
 * Renders the one Cursor rule file — {@code .cursor/rules/narrativetrace.mdc} — that points
 * Cursor's agent at the installed skills.
 *
 * <p>INTENT: the Cursor row's pointer. The rule is a SENTENCE, never a copy of a skill page: Cursor
 * already loads the pages from {@code .agents/skills}, and a second copy would be a third page
 * flavour to keep in step. The same text is the plugin's rule, so a project that installed the
 * plugin and one that ran {@code init} read the same words.
 *
 * <p><b>@llmNote</b> The file is frontmatter plus one managed section: Cursor needs the {@code ---}
 * block first, so the {@code narrativetrace:created} note a markdown home carries cannot go on line
 * 1. {@link #FRONTMATTER} is therefore also what an uninstall compares against — a rule file that
 * is nothing but the frontmatter once our section is removed is a file we made, and is deleted.
 * With an empty coordinate the opening marker carries no stamp, which is how the plugin ships it
 * and why {@code init} can adopt it.
 *
 * <p><b>@sideEffects</b> None. A pure rendering; renders with {@code \n} throughout.
 */
public final class CursorRule {

  /** The {@code .mdc} frontmatter: always applied, because the pointer must be seen every turn. */
  static final String FRONTMATTER =
      "---\ndescription: Where the NarrativeTrace agent skills live in this project\n"
          + "alwaysApply: true\n---\n";

  private CursorRule() {}

  /**
   * The whole rule file for one pointer sentence.
   *
   * @param text the pointer sentence, from the platform table's Cursor row
   * @param coordinate the carrier coordinate stamped on the opening marker, or {@code ""} for the
   *     unstamped plugin copy
   */
  public static String render(String text, String coordinate) {
    if (text == null || text.isBlank() || coordinate == null) {
      throw new IllegalArgumentException("a rule needs its text and a coordinate (or \"\")");
    }
    String stamp = coordinate.isEmpty() ? "" : " " + coordinate;
    String rule =
        FRONTMATTER + MarkedBlock.START + stamp + " -->\n" + text + "\n" + MarkedBlock.END + "\n";
    assert MarkedBlock.scan(rule).hasExactlyOneRegion() : "the rule must be one region";
    return rule;
  }
}
