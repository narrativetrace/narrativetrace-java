/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

import ai.narrativetrace.skills.MarketplaceListing;
import ai.narrativetrace.skills.OpenAiListing;
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
          NarrativeTraceDebugSkill.build(),
          NarrativeTraceExplainSkill.build());

  /**
   * How this repository presents {@link #ALL} to a plugin marketplace. One listing, whose name is
   * the repository's own runtime slug: marketplace name and plugin name are the same string because
   * a marketplace name is unique per user, and every runtime in the family ships skills under the
   * same canonical names.
   *
   * <p>The plugin's source is the rendered pages' own directory, so the plugin carries the seven
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
              + " cause of a wrong result by reading the values at each span, explain what an"
              + " unfamiliar flow actually does by running it and reading the trace, and report a"
              + " defect in NarrativeTrace itself with your approval.",
          "./.claude",
          "Apache-2.0",
          "https://narrativetrace.ai",
          List.of("narrativetrace", "java", "tracing", "observability", "agent-skills"));

  /**
   * How this repository presents {@link #MARKETPLACE} to the OpenAI plugin directory (shared by
   * ChatGPT and Codex): the same listing, plus the directory's own display fields. {@code
   * capabilities} names what the skills do — run a local build, read and write files — not a
   * network or data-access claim; {@code defaultPrompts} are drawn verbatim from the skills' own
   * trigger phrases in {@link #ALL}, never invented ones. {@code brandColor} is the brand bundle's
   * tile colour, and the two icon assets are the brand bundle's own avatar renders.
   */
  public static final OpenAiListing OPENAI_DIRECTORY =
      new OpenAiListing(
          MARKETPLACE,
          "NarrativeTrace for Java",
          "NarrativeTrace skills for Java",
          "Developer Tools",
          List.of("Read", "Write"),
          List.of(
              "add narrative tracing to my service",
              "check my narrativetrace setup",
              "debug this with the trace"),
          "#00845a",
          MARKETPLACE.homepage());

  private CatalogueIndex() {}
}
