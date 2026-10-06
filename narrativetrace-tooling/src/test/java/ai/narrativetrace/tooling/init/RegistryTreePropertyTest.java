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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * The properties that have to hold for a tree the installer did NOT create: the one a registry
 * leaves behind. Every try builds a real temp project shaped like a `npx skills add` install — the
 * rendered pages of one flavour, a symbolic link at the other flavour's path, and a lock file of
 * the registry's own — and then runs the installer over it.
 *
 * <p>Four dimensions, because each one has broken something in a port at some point: which
 * flavour's path is the link, whether the link is the DIRECTORY or the page inside it, and which
 * line ending the checked-out pages carry.
 */
class RegistryTreePropertyTest {

  /** What `npx skills add` writes at the project root; nothing of ours may touch it. */
  private static final String LOCK_FILE = "{\"skills\": []}\n";

  private static final Carrier CARRIER = Carriers.shared();

  // --- the properties ---------------------------------------------------------------------------

  /**
   * The whole of D5's promise: the pages are adopted, the link is replaced by a real path, and
   * NOTHING is written through the link — each flavour's path ends up holding its own flavour's
   * page, which is exactly what a write through the link would have destroyed.
   */
  @Property(tries = 40)
  void aRegistryTreeIsAdoptedWithoutWritingThroughItsLinks(
      @ForAll boolean linkTheVendorPath,
      @ForAll boolean linkThePage,
      @ForAll("lineEnding") String eol) {
    inATemporaryProject(
        project -> {
          registryTree(project, linkTheVendorPath, linkThePage, eol);

          ExecutionReport report = install(project);

          assertThat(report.exitCode()).as("a registry tree needs no flag").isZero();
          assertEveryPageIsItsOwnFlavour(project);
          assertThat(Files.readString(project.resolve("skills-lock.json"))).isEqualTo(LOCK_FILE);
        });
  }

  /**
   * Planning after adopting finds nothing left to do — idempotence, over a tree we did not write.
   */
  @Property(tries = 40)
  void planningAgainAfterAdoptingARegistryTreeFindsNothingToDo(
      @ForAll boolean linkTheVendorPath,
      @ForAll boolean linkThePage,
      @ForAll("lineEnding") String eol) {
    inATemporaryProject(
        project -> {
          registryTree(project, linkTheVendorPath, linkThePage, eol);
          install(project);
          Map<String, String> adopted = Projects.snapshotOf(project);

          InitPlan second = plan(project);
          install(project);

          assertThat(second.isEmpty()).as("%s", PlanRenderer.renderText(second)).isTrue();
          assertThat(Projects.snapshotOf(project)).isEqualTo(adopted);
        });
  }

  /**
   * Uninstalling gives back the registry's own tree minus the pages, which adoption made ours. The
   * lock file is the registry's and stays; a page identical to this release's was never anybody
   * else's work to keep.
   */
  @Property(tries = 40)
  void uninstallingAfterAdoptingLeavesTheRegistrysOwnFilesAndNoLink(
      @ForAll boolean linkTheVendorPath,
      @ForAll boolean linkThePage,
      @ForAll("lineEnding") String eol) {
    inATemporaryProject(
        project -> {
          registryTree(project, linkTheVendorPath, linkThePage, eol);
          install(project);

          ExecutionReport report =
              PlanExecutor.execute(
                  UninstallPlanner.plan(read(project), InitOptions.defaults()), project);

          assertThat(report.exitCode()).isZero();
          assertThat(Projects.snapshotOf(project)).containsOnlyKeys("skills-lock.json");
          assertThat(Projects.linksUnder(project)).isEmpty();
        });
  }

  // --- the tree a registry leaves ---------------------------------------------------------------

  /**
   * One flavour's pages for real, the other flavour's path a link to them, and the lock file. The
   * real pages are this carrier's own rendering — that is what a registry installs — carrying the
   * line ending of whoever checked them out.
   */
  private static void registryTree(
      Path project, boolean linkTheVendorPath, boolean linkThePage, String eol) throws IOException {
    SkillFlavour real = linkTheVendorPath ? SkillFlavour.AGENTS : SkillFlavour.CLAUDE;
    SkillFlavour linked = linkTheVendorPath ? SkillFlavour.CLAUDE : SkillFlavour.AGENTS;
    Files.createDirectories(project.resolve(linked.installRoot()));
    for (SkillEntry skill : CARRIER.skills()) {
      Path page = project.resolve(real.installRoot()).resolve(skill.name()).resolve("SKILL.md");
      Files.createDirectories(page.getParent());
      Files.writeString(page, CARRIER.body(skill, real).replace("\n", eol));
      link(project.resolve(linked.installRoot()).resolve(skill.name()), page, linkThePage);
    }
    Files.writeString(project.resolve("skills-lock.json"), LOCK_FILE);
  }

  /** The link a registry makes: the directory itself, or a real directory holding a linked page. */
  private static void link(Path at, Path page, boolean linkThePage) throws IOException {
    if (!linkThePage) {
      Files.createSymbolicLink(at, page.getParent());
      return;
    }
    Files.createDirectories(at);
    Files.createSymbolicLink(at.resolve("SKILL.md"), page);
  }

  /**
   * The assertion a write through a link would fail: every flavour's own path holds its OWN
   * flavour's stamped page, as a real file.
   */
  private static void assertEveryPageIsItsOwnFlavour(Path project) throws IOException {
    for (SkillEntry skill : CARRIER.skills()) {
      for (SkillFlavour flavour : SkillFlavour.values()) {
        Path page =
            project.resolve(flavour.installRoot()).resolve(skill.name()).resolve("SKILL.md");
        assertThat(Files.isSymbolicLink(page)).as("%s is a real file", page).isFalse();
        assertThat(Files.readString(page))
            .as("%s holds the %s flavour", page, flavour)
            .isEqualTo(Provenance.stamp(CARRIER.body(skill, flavour), CARRIER.coordinate()));
      }
    }
  }

  @Provide
  Arbitrary<String> lineEnding() {
    return Arbitraries.of("\n", "\r\n");
  }

  // --- the project under test -------------------------------------------------------------------

  private static void inATemporaryProject(Projects.Case body) {
    Projects.inATemporaryOne("narrativetrace-registry", body);
  }

  private static ProjectState read(Path project) {
    return ProjectStateReader.read(project);
  }

  private static InitPlan plan(Path project) {
    return InitPlanner.plan(read(project), CARRIER, InitOptions.defaults());
  }

  private static ExecutionReport install(Path project) {
    return PlanExecutor.execute(plan(project), project);
  }
}
