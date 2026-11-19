/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.catalogue;

import ai.narrativetrace.skills.FailureNote;
import ai.narrativetrace.skills.SkillStep;
import ai.narrativetrace.skills.StepBody;
import java.util.List;

/**
 * The newest-release lookup the Java skills that write a NarrativeTrace version into the user's
 * build share: one step, one URL, one command, so the pages and the plugin README that discloses
 * the fetch cannot disagree about it.
 *
 * <p>The skills pin no version. A pin in a skill page would be rewritten by every release and would
 * name a number that is not on Maven Central until the release lands. The agent instead reads
 * {@code <release>} from the core artifact's {@code maven-metadata.xml} and writes that exact
 * number wherever a coordinate carries the {@code <version>} placeholder. One lookup is enough
 * because every published library and the {@code ai.narrativetrace} Gradle plugin (its marker
 * artifact included) are built at the single {@code narrativetraceVersion} of {@code
 * gradle.properties} and published together — checked against the build and against Maven Central's
 * metadata for the core, proxy, slf4j, clarity, junit5 and plugin artifacts when this was written.
 *
 * <p><b>@llmNote</b> {@code curl} is outside the skills' closed command vocabulary ({@code
 * CommandVocabulary#JAVA}) on purpose: the lookup is a code block the agent is told to run, not a
 * {@code CommandStep}, so the replay and the vocabulary lint never treat it as one of the skill's
 * own commands. The plugin README names it explicitly instead.
 */
public final class ReleaseLookup {

  /** The core artifact's metadata on Maven Central; its {@code <release>} is the newest release. */
  public static final String METADATA_URL =
      "https://repo1.maven.org/maven2/ai/narrativetrace/narrativetrace-core/maven-metadata.xml";

  /** The placeholder every coordinate in a skill page carries instead of a version number. */
  public static final String PLACEHOLDER = "<version>";

  /** A one-line POSIX command that prints the newest release number. */
  public static final String COMMAND =
      "curl -s " + METADATA_URL + " | grep -o '<release>[^<]*' | cut -d'>' -f2";

  public static final String TITLE = "Resolve the newest NarrativeTrace release";

  private ReleaseLookup() {}

  /** The unconditional step, first in a skill that installs NarrativeTrace. */
  public static SkillStep step() {
    return step(null);
  }

  /** The same step, applying only when {@code condition} holds (null for always). */
  public static SkillStep step(String condition) {
    return new SkillStep(
        TITLE,
        new StepBody.CodeStep("bash", COMMAND),
        "the command printed one release number, digits and dots; write that exact number wherever"
            + " a coordinate below says `"
            + PLACEHOLDER
            + "`, in every NarrativeTrace dependency and plugin line you add — the libraries and"
            + " the Gradle plugin are published at the same version, so this one lookup serves them"
            + " all; never write a number from memory, from a document, or from an older pin in the"
            + " project. If curl is not available, open "
            + METADATA_URL
            + " and read its `<release>` element",
        List.of(
            new FailureNote(
                "the command prints nothing, or an error",
                "no network, or a proxy that blocks repo1.maven.org",
                "read the `<release>` element of "
                    + METADATA_URL
                    + " with any other fetch tool; if it cannot be reached at all, stop and tell"
                    + " the user instead of guessing a version")),
        null,
        condition);
  }
}
