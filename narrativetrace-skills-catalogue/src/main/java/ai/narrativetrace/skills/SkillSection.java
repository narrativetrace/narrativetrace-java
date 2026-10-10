/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills;

/**
 * A named block of reference text a skill renders after its steps — a table or a short list the
 * steps point at, which is neither a step nor an always/never rule.
 *
 * <p>INTENT: two skills that read traces share one "how to read a trace" text; a section is how
 * that text is written once in the catalogue and rendered into both pages identically, instead of
 * being copied into a step's prose where the copies drift.
 *
 * @param heading the section's heading text, rendered as a level-two heading — one line, and not
 *     itself a heading marker
 * @param markdown the section's body, rendered as written
 */
public record SkillSection(String heading, String markdown) {

  public SkillSection {
    if (heading == null || heading.isBlank()) {
      throw new IllegalArgumentException("a SkillSection's heading must not be blank");
    }
    if (heading.contains("\n") || heading.contains("\r") || heading.startsWith("#")) {
      throw new IllegalArgumentException(
          "a SkillSection's heading is one line of text, not markdown: " + heading);
    }
    if (markdown == null || markdown.isBlank()) {
      throw new IllegalArgumentException("a SkillSection's markdown must not be blank");
    }
  }
}
