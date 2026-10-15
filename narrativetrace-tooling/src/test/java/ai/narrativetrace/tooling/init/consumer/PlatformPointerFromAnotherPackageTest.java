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

import ai.narrativetrace.tooling.init.Action;
import ai.narrativetrace.tooling.init.Carrier;
import ai.narrativetrace.tooling.init.CursorRule;
import ai.narrativetrace.tooling.init.InitOptions;
import ai.narrativetrace.tooling.init.InitPlan;
import ai.narrativetrace.tooling.init.InitPlanner;
import ai.narrativetrace.tooling.init.PlanExecutor;
import ai.narrativetrace.tooling.init.ProjectStateReader;
import ai.narrativetrace.tooling.init.UninstallPlanner;
import ai.narrativetrace.tooling.platforms.PlatformTable;
import ai.narrativetrace.tooling.platforms.Pointer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The platform pointers as an ENTRY POINT sees them: another package, only the public installer
 * API, the real published carrier, and a real directory the real installer wrote into.
 *
 * <p>Same-package tests reach package-private helpers such as the marker scanner. These do not, so
 * they can only prove what a consumer of the installer can rely on: that the pointer rule the
 * installer writes is the one the plugin ships, and that install and uninstall are inverses on real
 * bytes.
 */
class PlatformPointerFromAnotherPackageTest {

  private static final Path PROJECT_DIR = Path.of(System.getProperty("projectDir"));
  private static final String VERSION = System.getProperty("narrativetrace.buildVersion");
  private static final String COORDINATE = "ai.narrativetrace:narrativetrace-skills:" + VERSION;
  private static final String RULE = ".cursor/rules/narrativetrace.mdc";
  private static final String REPLIT_MD = "replit.md";
  private static final String CREATED = "<!-- narrativetrace:created -->\n";

  @TempDir Path root;

  private Path project;

  @BeforeEach
  void setUp() throws IOException {
    project = Files.createDirectories(root.resolve("project"));
  }

  // --- helpers ----------------------------------------------------------------------------------

  private static Carrier realCarrier() {
    return Carrier.open(
        PROJECT_DIR.resolve(
            "narrativetrace-skills/build/libs/narrativetrace-skills-" + VERSION + ".jar"));
  }

  private InitPlan planInstall() {
    return InitPlanner.plan(
        ProjectStateReader.read(project), realCarrier(), InitOptions.defaults());
  }

  private InitPlan planUninstall() {
    return UninstallPlanner.plan(ProjectStateReader.read(project), InitOptions.defaults());
  }

  private void install() {
    PlanExecutor.execute(planInstall(), project);
  }

  private void uninstall() {
    PlanExecutor.execute(planUninstall(), project);
  }

  private void write(String relative, String content) throws IOException {
    Path file = project.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  private String read(String relative) throws IOException {
    return Files.readString(project.resolve(relative));
  }

  private List<String> files() throws IOException {
    try (Stream<Path> walk = Files.walk(project)) {
      return walk.filter(Files::isRegularFile)
          .map(file -> project.relativize(file).toString())
          .sorted()
          .toList();
    }
  }

  private static String cursorText() {
    return ((Pointer.Rule) PlatformTable.row("cursor").orElseThrow().pointer()).text();
  }

  private static String crlf(String text) {
    return text.replace("\n", "\r\n");
  }

  // --- what the real install writes -------------------------------------------------------------

  @Test
  void theRealInstallWritesTheCursorRuleThePluginShipsStampedWithTheBuild() throws IOException {
    Files.createDirectories(project.resolve(".cursor"));

    install();

    assertThat(read(RULE)).isEqualTo(CursorRule.render(cursorText(), COORDINATE));
  }

  @Test
  void aReplitDirectoryMakesTheRealInstallCreateAStampedReplitMdWithTheCreatedNote()
      throws IOException {
    Files.createDirectories(project.resolve(".replit"));

    install();

    assertThat(read(REPLIT_MD))
        .startsWith(CREATED + "<!-- narrativetrace:start " + COORDINATE + " -->\n");
  }

  @Test
  void aSecondRealInstallOverAnInstalledProjectPlansNothing() throws IOException {
    Files.createDirectories(project.resolve(".cursor"));
    install();

    assertThat(planInstall().actions()).isEmpty();
  }

  @Test
  void aPluginShippedCursorRuleIsAdoptedByTheRealInstallWithoutForce() throws IOException {
    write(RULE, CursorRule.render(cursorText(), ""));

    InitPlan plan = planInstall();

    assertThat(plan.hasRefusals()).isFalse();
    PlanExecutor.execute(plan, project);
    assertThat(read(RULE)).isEqualTo(CursorRule.render(cursorText(), COORDINATE));
  }

  @Test
  void aCursorRuleWithTwoSectionsIsRefusedByTheRealInstallAndNotRewritten() throws IOException {
    String rule = CursorRule.render(cursorText(), COORDINATE);
    write(RULE, rule + "\n" + rule);

    InitPlan plan = planInstall();

    assertThat(plan.refusals()).extracting(Action.Refuse::path).contains(Path.of(RULE));
    PlanExecutor.execute(plan, project);
    assertThat(read(RULE)).isEqualTo(rule + "\n" + rule);
  }

  // --- install and uninstall are inverses on real bytes -----------------------------------------

  @Test
  void theRealInstallThenUninstallLeavesACrlfReplitMdAndNoOtherFileBehind() throws IOException {
    write(".replit", "run = \"x\"\n");
    write(REPLIT_MD, crlf("# Notes\n\nHello\n"));
    List<String> before = files();

    install();
    uninstall();

    assertThat(files()).isEqualTo(before);
    assertThat(read(REPLIT_MD)).isEqualTo(crlf("# Notes\n\nHello\n"));
  }

  @Test
  void theRealUninstallDeletesACursorRuleTheUserConvertedToCrlf() throws IOException {
    Files.createDirectories(project.resolve(".cursor"));
    install();
    write(RULE, crlf(read(RULE)));

    uninstall();

    assertThat(project.resolve(RULE)).doesNotExist();
  }

  // --- links and the uninstaller ----------------------------------------------------------------

  @Test
  void anUninstallNeverReadsOrRemovesACursorRuleThatSitsBehindALink() throws IOException {
    Path elsewhere = Files.createDirectories(root.resolve("elsewhere"));
    Files.writeString(
        elsewhere.resolve("narrativetrace.mdc"), CursorRule.render(cursorText(), COORDINATE));
    Files.createSymbolicLink(project.resolve(".cursor"), elsewhere);

    InitPlan plan = planUninstall();
    PlanExecutor.execute(plan, project);

    assertThat(plan.isEmpty()).isTrue();
    assertThat(elsewhere.resolve("narrativetrace.mdc")).exists();
  }
}
