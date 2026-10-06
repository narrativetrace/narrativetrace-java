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
package ai.narrativetrace.tooling.init.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.tooling.init.Carrier;
import ai.narrativetrace.tooling.init.ExecutionReport;
import ai.narrativetrace.tooling.init.InitOptions;
import ai.narrativetrace.tooling.init.InitPlan;
import ai.narrativetrace.tooling.init.InitPlanner;
import ai.narrativetrace.tooling.init.PlanExecutor;
import ai.narrativetrace.tooling.init.PlanRenderer;
import ai.narrativetrace.tooling.init.ProjectState;
import ai.narrativetrace.tooling.init.ProjectStateReader;
import ai.narrativetrace.tooling.init.SkillEntry;
import ai.narrativetrace.tooling.init.SkillFlavour;
import ai.narrativetrace.tooling.init.UninstallPlanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The installer as an ENTRY POINT sees it: another package, the real published carrier, a real
 * directory. Nothing here reaches into the package, so anything load-bearing that is accidentally
 * package-private fails to compile rather than passing in a same-package test.
 *
 * <p>These are also the only end-to-end cases: open the jar this build produced, install into an
 * empty project, install again, uninstall, and check the tree at each step.
 */
class InstallerFromAnotherPackageTest {

  private static final Path PROJECT_DIR = Path.of(System.getProperty("projectDir"));
  private static final String VERSION = System.getProperty("narrativetrace.buildVersion");

  /** What `npx skills add` writes at the project root, and nothing of ours may touch. */
  private static final String LOCK_FILE = "{\"skills\": []}\n";

  private static Carrier realCarrier() {
    return Carrier.open(
        PROJECT_DIR.resolve(
            "narrativetrace-skills/build/libs/narrativetrace-skills-" + VERSION + ".jar"));
  }

  private static ExecutionReport install(Path project, InitOptions options) {
    Carrier carrier = realCarrier();
    InitPlan plan = InitPlanner.plan(ProjectStateReader.read(project), carrier, options);
    return PlanExecutor.execute(plan, project);
  }

  private static List<String> filesUnder(Path project) throws IOException {
    try (Stream<Path> walk = Files.walk(project)) {
      return walk.filter(Files::isRegularFile)
          .map(file -> project.relativize(file).toString())
          .sorted()
          .toList();
    }
  }

  @Test
  void installsTheCarriersSkillsAndTheManagedSectionIntoAnEmptyProject(@TempDir Path project)
      throws IOException {
    Carrier carrier = realCarrier();

    ExecutionReport report = install(project, InitOptions.defaults());

    assertThat(report.exitCode()).isZero();
    assertThat(report.carrier()).isEqualTo("ai.narrativetrace:narrativetrace-skills:" + VERSION);
    for (SkillEntry skill : carrier.skills()) {
      Path page = project.resolve(".agents/skills/" + skill.name() + "/SKILL.md");
      assertThat(page).exists();
      assertThat(Files.readString(page))
          .contains("installed by narrativetrace init from " + carrier.coordinate())
          .contains(skill.name());
    }
    assertThat(Files.readString(project.resolve("AGENTS.md")))
        .startsWith("<!-- narrativetrace:created -->")
        .contains("<!-- narrativetrace:start " + carrier.coordinate() + " -->")
        .contains("<!-- narrativetrace:end -->");
  }

  @Test
  void installsTheVendorFlavourOnlyWhereItIsWanted(@TempDir Path project) throws IOException {
    Files.createDirectories(project.resolve(".claude"));

    install(project, InitOptions.defaults());

    assertThat(filesUnder(project))
        .anyMatch(file -> file.startsWith(".claude/skills/"))
        .anyMatch(file -> file.startsWith(".agents/skills/"));
  }

  @Test
  void aSecondInstallOfTheSameCarrierHasNothingToDo(@TempDir Path project) {
    install(project, InitOptions.defaults());

    InitPlan second =
        InitPlanner.plan(ProjectStateReader.read(project), realCarrier(), InitOptions.defaults());

    assertThat(second.isEmpty()).isTrue();
    assertThat(PlanRenderer.renderText(second)).contains("nothing to do");
    assertThat(PlanRenderer.renderDiff(second)).isEmpty();
  }

  @Test
  void aDryRunShowsTheDiffAndWritesNothing(@TempDir Path project) throws IOException {
    InitPlan plan =
        InitPlanner.plan(
            ProjectStateReader.read(project),
            realCarrier(),
            InitOptions.defaults().withDryRun(true));

    String diff = PlanRenderer.renderDiff(plan);

    assertThat(diff)
        .contains("--- /dev/null")
        .contains("+++ b/AGENTS.md")
        .contains("+## NarrativeTrace");
    assertThat(filesUnder(project)).isEmpty();
    assertThat(plan.exitCode()).isZero();
  }

  @Test
  void uninstallingTakesBackEverythingTheInstallWrote(@TempDir Path project) throws IOException {
    install(project, InitOptions.defaults());

    ExecutionReport report =
        PlanExecutor.execute(
            UninstallPlanner.plan(ProjectStateReader.read(project), InitOptions.defaults()),
            project);

    assertThat(report.exitCode()).isZero();
    assertThat(filesUnder(project)).isEmpty();
  }

  @Test
  void refusesAnExistingContextFileAndSaysWhatToPass(@TempDir Path project) throws IOException {
    Files.writeString(project.resolve("AGENTS.md"), "# My own notes\n");

    ExecutionReport report = install(project, InitOptions.defaults());

    assertThat(report.exitCode()).isEqualTo(1);
    assertThat(PlanRenderer.renderText(report)).contains("--write-existing");
    assertThat(Files.readString(project.resolve("AGENTS.md"))).isEqualTo("# My own notes\n");
    assertThat(filesUnder(project)).anyMatch(file -> file.startsWith(".agents/skills/"));
  }

  @Test
  void theJsonEnvelopeNamesTheCarrierEveryActionAndTheExitCode(@TempDir Path project) {
    ExecutionReport report = install(project, InitOptions.defaults());

    String json = PlanRenderer.renderJson(report);

    assertThat(json)
        .startsWith("{\n  \"carrier\": \"ai.narrativetrace:narrativetrace-skills:" + VERSION + "\"")
        .contains("\"kind\": \"create\", \"path\": \"AGENTS.md\", \"status\": \"applied\"")
        .endsWith("\"exitCode\": 0\n}\n");
  }

  @Test
  void installsOnlyTheHalfItIsAskedFor(@TempDir Path project) throws IOException {
    install(project, InitOptions.defaults().withScope(InitOptions.Scope.SKILLS));

    assertThat(filesUnder(project)).isNotEmpty().noneMatch("AGENTS.md"::equals);
  }

  /**
   * The registry case, end to end: a project that got these very pages from a registry — the
   * repository's own rendered files, no provenance line — is adopted rather than refused, with no
   * flag at all, and the page it already had is left in place with one line added.
   */
  @Test
  void adoptsThePagesARegistryInstalledWithoutAnyFlag(@TempDir Path project) throws IOException {
    Carrier carrier = realCarrier();
    SkillEntry first = carrier.skills().get(0);
    Path page = project.resolve(".agents/skills/" + first.name() + "/SKILL.md");
    Files.createDirectories(page.getParent());
    Files.writeString(page, carrier.body(first, SkillFlavour.AGENTS));

    ExecutionReport report = install(project, InitOptions.defaults());

    assertThat(report.exitCode()).isZero();
    assertThat(PlanRenderer.renderText(report)).contains("adopt").contains("adopted:");
    String stamped = Files.readString(page);
    assertThat(stamped).contains("installed by narrativetrace init from " + carrier.coordinate());
    assertThat(withoutProvenance(stamped))
        .as("adoption adds one line and changes nothing else")
        .isEqualTo(carrier.body(first, SkillFlavour.AGENTS));
  }

  /** The page with the installer's own line taken back out, to compare against what was there. */
  private static String withoutProvenance(String page) {
    return page.lines()
        .filter(line -> !line.startsWith("<!-- installed by narrativetrace init from "))
        .map(line -> line + "\n")
        .collect(java.util.stream.Collectors.joining());
  }

  /**
   * The whole registry tree, end to end: what `npx skills add` really leaves — the open-standard
   * pages, a LINK from each vendor path to them, and a lock file of its own. `init` adopts the
   * pages and replaces the links; it never writes the vendor flavour through one, which would have
   * overwritten the page it had just adopted.
   */
  @Test
  void installsIntoATreeARegistryLeftBehindWithoutWritingThroughItsLinks(@TempDir Path project)
      throws IOException {
    Carrier carrier = realCarrier();
    simulateRegistryInstall(project, carrier);

    ExecutionReport report = install(project, InitOptions.defaults());

    assertThat(report.exitCode()).isZero();
    for (SkillEntry skill : carrier.skills()) {
      Path agents = project.resolve(".agents/skills/" + skill.name() + "/SKILL.md");
      Path vendor = project.resolve(".claude/skills/" + skill.name());
      assertThat(Files.readString(agents))
          .as("the adopted open-standard page, not the vendor flavour written through the link")
          .contains(carrier.coordinate())
          .isEqualTo(stamped(carrier.body(skill, SkillFlavour.AGENTS), carrier));
      assertThat(Files.isSymbolicLink(vendor)).isFalse();
      assertThat(Files.readString(vendor.resolve("SKILL.md")))
          .isEqualTo(stamped(carrier.body(skill, SkillFlavour.CLAUDE), carrier));
    }
    assertThat(Files.readString(project.resolve("skills-lock.json"))).isEqualTo(LOCK_FILE);
  }

  /** Uninstalling that tree takes back the pages, now ours, and leaves the registry's own file. */
  @Test
  void uninstallingAfterAdoptingARegistryTreeLeavesTheRegistrysOwnFiles(@TempDir Path project)
      throws IOException {
    Carrier carrier = realCarrier();
    simulateRegistryInstall(project, carrier);
    install(project, InitOptions.defaults());

    ExecutionReport report =
        PlanExecutor.execute(
            UninstallPlanner.plan(ProjectStateReader.read(project), InitOptions.defaults()),
            project);

    assertThat(report.exitCode()).isZero();
    assertThat(filesUnder(project)).containsExactly("skills-lock.json");
  }

  /** `npx skills add`: the agents pages, a link per vendor path, and a lock file at the root. */
  private static void simulateRegistryInstall(Path project, Carrier carrier) throws IOException {
    Files.createDirectories(project.resolve(".claude/skills"));
    for (SkillEntry skill : carrier.skills()) {
      Path directory = project.resolve(".agents/skills/" + skill.name());
      Files.createDirectories(directory);
      Files.writeString(directory.resolve("SKILL.md"), carrier.body(skill, SkillFlavour.AGENTS));
      Files.createSymbolicLink(
          project.resolve(".claude/skills/" + skill.name()),
          Path.of("../../.agents/skills/" + skill.name()));
    }
    Files.writeString(project.resolve("skills-lock.json"), LOCK_FILE);
  }

  /** The page as an install of this carrier leaves it: the rendering plus the one line. */
  private static String stamped(String rendered, Carrier carrier) {
    int frontmatter = rendered.indexOf("---\n", 4) + 4;
    return rendered.substring(0, frontmatter)
        + "<!-- installed by narrativetrace init from "
        + carrier.coordinate()
        + " — edit the catalogue, not this file -->\n"
        + rendered.substring(frontmatter);
  }

  @Test
  void readsBackWhatItWroteAsAProjectState(@TempDir Path project) {
    install(project, InitOptions.defaults());

    ProjectState state = ProjectStateReader.read(project);

    assertThat(state.agentsMd()).isPresent();
    assertThat(state.installedSkills())
        .isNotEmpty()
        .allSatisfy(
            skill ->
                assertThat(skill.coordinate())
                    .isEqualTo("ai.narrativetrace:narrativetrace-skills:" + VERSION));
  }
}
