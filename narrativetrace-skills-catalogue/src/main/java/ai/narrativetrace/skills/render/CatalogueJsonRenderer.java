/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import ai.narrativetrace.skills.Skill;
import java.util.List;

/**
 * Renders the carrier's {@code catalogue.json}: which skills the jar carries, and where each
 * flavour's {@code SKILL.md} sits inside it. The index an installer reads FIRST — it never walks
 * the jar guessing at directory names.
 *
 * <p>No version anywhere, deliberately (Phase 2 D2): the carrier jar's own coordinate is the stamp,
 * and a version literal in a checked-in file would drift on every release. {@code
 * CatalogueJsonRendererTest} holds that as an assertion, not as a convention.
 *
 * <p>JSON is written by hand because this module takes zero production dependencies, the same
 * contract {@code narrativetrace-api} and {@code narrativetrace-tooling} hold. The shape is small
 * and fixed; string encoding is {@link JsonText}'s, shared with every other JSON render target so
 * two targets cannot escape the same description differently.
 */
public final class CatalogueJsonRenderer {

  /** Which runtime's skills these are — the family ships one carrier per port. */
  private static final String RUNTIME = "java";

  private CatalogueJsonRenderer() {}

  public static String render(List<Skill> skills) {
    StringBuilder out = new StringBuilder();
    out.append("{\n");
    out.append("  \"runtime\": ").append(JsonText.quote(RUNTIME)).append(",\n");
    if (skills.isEmpty()) {
      out.append("  \"skills\": []\n}\n");
      return out.toString();
    }
    out.append("  \"skills\": [\n");
    for (int i = 0; i < skills.size(); i++) {
      appendSkill(out, skills.get(i));
      out.append(i == skills.size() - 1 ? "\n" : ",\n");
    }
    out.append("  ]\n}\n");
    return out.toString();
  }

  private static void appendSkill(StringBuilder out, Skill skill) {
    out.append("    {\n");
    out.append("      \"name\": ").append(JsonText.quote(skill.canonicalName())).append(",\n");
    out.append("      \"description\": ").append(JsonText.quote(skill.description())).append(",\n");
    out.append("      \"agents\": ")
        .append(JsonText.quote(RenderPaths.carrierAgentsResource(skill)));
    out.append(",\n");
    out.append("      \"claude\": ")
        .append(JsonText.quote(RenderPaths.carrierClaudeResource(skill)));
    out.append("\n    }");
  }
}
