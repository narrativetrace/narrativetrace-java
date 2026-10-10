/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

import ai.narrativetrace.skills.MarketplaceListing;
import ai.narrativetrace.skills.Skill;
import java.util.List;

/** The assembled free catalogue — every {@link Skill} this repo ships, in rendering order. */
public final class CatalogueIndex {

  public static final List<Skill> ALL =
      List.of(
          NarrativeTraceDoctorSkill.build(),
          AddNarrativeTracingSkill.build(),
          AddNarrativeTraceClaritySkill.build(),
          NarrativeTraceFeedbackSkill.build(),
          NarrativeTraceVerifySkill.build(),
          NarrativeTraceDebugSkill.build());

  /**
   * How this repository presents {@link #ALL} to a plugin marketplace. One listing, whose name is
   * the repository's own runtime slug: marketplace name and plugin name are the same string because
   * a marketplace name is unique per user, and every runtime in the family ships skills under the
   * same canonical names.
   *
   * <p>The plugin's source is the rendered pages' own directory, so the plugin carries the six
   * skill pages and nothing else of this repository.
   */
  public static final MarketplaceListing MARKETPLACE =
      new MarketplaceListing(
          "narrativetrace-java",
          new MarketplaceListing.Owner("NarrativeTrace", "https://narrativetrace.ai"),
          "The NarrativeTrace agent skills for Java.",
          "Install NarrativeTrace in a Java project and reach a first trace, diagnose an install"
              + " that traces nothing, add or verify a Clarity naming report, verify a change by"
              + " reading what its code actually did and pin it as an approval baseline, find the"
              + " cause of a wrong result by reading the values at each span, and report a defect"
              + " in NarrativeTrace itself with your approval.",
          "./.claude",
          "Apache-2.0",
          "https://narrativetrace.ai",
          List.of("narrativetrace", "java", "tracing", "observability", "agent-skills"));

  private CatalogueIndex() {}
}
