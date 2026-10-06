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
package ai.narrativetrace.tooling.doctor.checks;

import ai.narrativetrace.tooling.doctor.DocAnchors;
import ai.narrativetrace.tooling.doctor.DoctorCheck;
import ai.narrativetrace.tooling.doctor.DoctorSnapshot;
import ai.narrativetrace.tooling.doctor.Finding;
import ai.narrativetrace.tooling.init.InstalledSkill;
import ai.narrativetrace.tooling.init.SkillFlavour;
import java.util.List;
import java.util.Optional;

/**
 * {@code config.skills-installed} — the NarrativeTrace agent skills this project's carrier ships
 * are installed under {@code .agents/skills/}, and stamped with the carrier the project actually
 * resolves.
 *
 * <p>INTENT: a project whose skills are absent gets an agent that improvises the library's
 * procedures; a project whose skills are a release behind gets an agent following commands that may
 * no longer exist. Both are silent failures with no symptom of their own, which is exactly the
 * shape of thing the doctor exists to say out loud.
 *
 * <p>Read-only, like every check. It reads what the installer's own reader found — a page is "ours"
 * only when it carries the provenance line {@code init} writes — so the doctor and the installer
 * can never disagree about which directory belongs to whom.
 *
 * <p><b>@llmNote</b> The one outcome that is NOT a failure is a carrier nobody could resolve: an
 * offline build, or a build with no repository that provides it. The doctor never guesses, so it
 * says it cannot tell and passes — the same two-state shape {@link Junit5RangeCheck} uses for a
 * project that declares no JUnit at all. A project that simply never ran the installer is a
 * FAILURE, not an exemption: the doctor's audience is a project that already has the library.
 *
 * <p><b>@llmNote</b> "Current" means the same RELEASE, not the same coordinate: the two entry
 * points install the identical pages out of different archives, so comparing whole coordinates
 * would call every cross-entry-point install stale. See {@code staleCoordinates}.
 *
 * <p><b>@llmNote</b> Only {@code .agents/skills/} counts. The vendor copy is written only where a
 * project is detected as that vendor's, so a project carrying the vendor copy alone is a project
 * the open-standard install never reached.
 */
public final class SkillsInstalledCheck implements DoctorCheck {

  public static final String ID = "config.skills-installed";

  // Quoted verbatim by documentation/agent-skills.md's "From a registry" section (rule 8, docs as
  // tests) — the marker pair around these three fields is that embed's source, never typed into
  // the page.
  // snippet:begin registryMessages
  /** Both spellings of the same command: Gradle owns {@code --dry-run}, so the task says diff. */
  private static final String INIT_COMMANDS =
      "./gradlew narrativetraceInit --diff (or narrativetrace init --dry-run)";

  /** What a skill directory that is present but not ours is called in a message. */
  private static final String NOT_OURS = " (there, but not ours)";

  /**
   * What a page with no provenance line most often IS: a registry install (D5 state 3) — `npx
   * skills add`, or a workspace skills install — of this repository's own rendered pages. Naming
   * the case matters because the obvious reading of "not ours" is "somebody else's work", which
   * invites a `--force` nobody needs: `init` ADOPTS a page identical to this release's.
   */
  private static final String FROM_A_REGISTRY =
      " Pages that are there without our line usually came from a registry (npx skills add, a"
          + " plugin or workspace install). Run "
          + INIT_COMMANDS
          + ", read the diff, then run it without the flag — a page identical to this release's is"
          + " adopted, and no --force is needed.";

  // snippet:end registryMessages

  @Override
  public Finding run(DoctorSnapshot snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("the skills check needs a snapshot to read");
    }
    Finding finding = decide(snapshot);
    assert !finding.isFailing() || snapshot.carrierResolved()
        : "a carrier nobody could resolve is never a project's defect";
    return finding;
  }

  /** Worst first: nothing installed, then a wrong stamp, then a partial install. */
  private static Finding decide(DoctorSnapshot snapshot) {
    if (!snapshot.carrierResolved()) {
      return Finding.pass(
          ID,
          "The skills carrier could not be resolved — cannot tell whether this project's agent"
              + " skills are current",
          DocAnchors.AGENT_SKILLS_INSTALLING);
    }
    List<InstalledSkill> ours = ours(snapshot);
    if (ours.isEmpty()) {
      return notInstalled(snapshot);
    }
    List<String> stale = staleCoordinates(ours, snapshot.carrierCoordinate());
    if (!stale.isEmpty()) {
      return stale(stale, snapshot.carrierCoordinate());
    }
    List<String> missing = missing(snapshot);
    return missing.isEmpty() ? upToDate(ours, snapshot) : incomplete(missing);
  }

  private static Finding upToDate(List<InstalledSkill> ours, DoctorSnapshot snapshot) {
    return Finding.pass(
        ID,
        "All " + ours.size() + " agent skill(s) are installed from " + snapshot.carrierCoordinate(),
        DocAnchors.AGENT_SKILLS_INSTALLING);
  }

  private static Finding notInstalled(DoctorSnapshot snapshot) {
    List<String> foreign = present(snapshot);
    String fix = "Run " + INIT_COMMANDS + ", read the diff, then run it without the flag.";
    return Finding.fail(
        ID,
        "The NarrativeTrace agent skills are not installed under "
            + SkillFlavour.AGENTS.installRoot()
            + "/"
            + (foreign.isEmpty() ? "" : " — " + String.join(", ", foreign) + " is there, not ours"),
        foreign.isEmpty() ? fix : fix + FROM_A_REGISTRY,
        DocAnchors.AGENT_SKILLS_INSTALLING);
  }

  private static Finding stale(List<String> stale, String resolved) {
    return Finding.fail(
        ID,
        "Agent skills installed from "
            + String.join(", ", stale)
            + ", project resolves "
            + resolved,
        "Re-run "
            + INIT_COMMANDS
            + " and apply it — the installed pages describe a different release of NarrativeTrace"
            + " than this project uses.",
        DocAnchors.AGENT_SKILLS_INSTALLING);
  }

  private static Finding incomplete(List<String> missing) {
    return Finding.fail(
        ID,
        "The agent skills are installed, but this carrier's "
            + String.join(", ", missing)
            + " is missing",
        "Run "
            + INIT_COMMANDS
            + " to add the missing page(s). A page identical to this release's is adopted as it"
            + " stands; --force is only for a directory somebody else really owns.",
        DocAnchors.AGENT_SKILLS_INSTALLING);
  }

  /** The catalogue's skills that are installed in this project and carry our provenance line. */
  private static List<InstalledSkill> ours(DoctorSnapshot snapshot) {
    return snapshot.catalogueSkills().stream()
        .map(name -> found(snapshot, name))
        .flatMap(Optional::stream)
        .filter(skill -> skill.presence() == InstalledSkill.Presence.OURS)
        .toList();
  }

  /** The catalogue's skills that are absent, or present as somebody else's — named for a reader. */
  private static List<String> missing(DoctorSnapshot snapshot) {
    return snapshot.catalogueSkills().stream()
        .filter(name -> !isOurs(snapshot, name))
        .map(name -> found(snapshot, name).isPresent() ? name + NOT_OURS : name)
        .toList();
  }

  /**
   * The catalogue's skills that have a directory in this project. Asked only from {@link
   * #notInstalled}, where nothing of ours is installed by construction — so "it is there" already
   * means "it is somebody else's", and a second filter saying so would be unreachable.
   */
  private static List<String> present(DoctorSnapshot snapshot) {
    return snapshot.catalogueSkills().stream()
        .filter(name -> found(snapshot, name).isPresent())
        .toList();
  }

  /**
   * Every distinct carrier the installed pages name that is not the RELEASE the project resolves.
   *
   * <p>Release, not coordinate: the two entry points install the same pages out of different
   * archives — the Gradle plugin from the carrier jar, the CLI from its own jar, which bundles the
   * identical resources — so a project installed by one and diagnosed by the other would otherwise
   * be reported stale for a difference that changes nothing in the page.
   */
  private static List<String> staleCoordinates(List<InstalledSkill> ours, String resolved) {
    String release = releaseOf(resolved);
    return ours.stream()
        .map(InstalledSkill::coordinate)
        .filter(coordinate -> !releaseOf(coordinate).equals(release))
        .distinct()
        .sorted()
        .toList();
  }

  /**
   * The version segment of a {@code group:artifact:version} stamp — and only of one. A stamp with a
   * segment missing or one too many names no release anybody can place, so it is compared as it
   * stands and reads as stale, which is what a page nobody can place should read as. Taking the
   * text after the last separator regardless would call {@code :1.2.3} current.
   */
  private static String releaseOf(String coordinate) {
    String[] parts = coordinate.split(":", -1);
    return parts.length == 3 ? parts[2] : coordinate;
  }

  private static Optional<InstalledSkill> found(DoctorSnapshot snapshot, String name) {
    return snapshot.installedSkill(SkillFlavour.AGENTS, name);
  }

  private static boolean isOurs(DoctorSnapshot snapshot, String name) {
    return found(snapshot, name)
        .filter(skill -> skill.presence() == InstalledSkill.Presence.OURS)
        .isPresent();
  }
}
