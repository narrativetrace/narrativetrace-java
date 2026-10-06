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
package ai.narrativetrace.cli;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.tooling.doctor.DoctorSnapshot;
import ai.narrativetrace.tooling.init.Carrier;
import ai.narrativetrace.tooling.init.InstalledSkill;
import ai.narrativetrace.tooling.init.SkillFlavour;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTest {

  private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
  private final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
  private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

  private Cli.Deps deps(Function<Path, DoctorSnapshot> snapshotFn) {
    return new Cli.Deps(Path.of("."), snapshotFn, from -> noCarrier(), () -> false, out, err);
  }

  private static Carrier noCarrier() {
    throw new IllegalStateException("this command must not open a carrier");
  }

  private String stdout() {
    return outBytes.toString(StandardCharsets.UTF_8);
  }

  private String stderr() {
    return errBytes.toString(StandardCharsets.UTF_8);
  }

  // --- the launcher itself --------------------------------------------------------------------

  @Test
  void noArgsPrintsUsageAndExits2() {
    int code = Cli.run(new String[0], deps(p -> DoctorSnapshot.healthy()));
    assertThat(code).isEqualTo(2);
    assertThat(stderr()).contains("narrativetrace doctor [--json]");
  }

  @Test
  void topLevelHelpExits0AndListsEveryVerb() {
    assertThat(Cli.run(new String[] {"--help"}, deps(p -> DoctorSnapshot.healthy()))).isZero();
    assertThat(stdout()).contains("Usage:").contains("init").contains("uninstall");
    outBytes.reset();
    assertThat(Cli.run(new String[] {"-h"}, deps(p -> DoctorSnapshot.healthy()))).isZero();
    assertThat(stdout()).contains("Usage:");
  }

  @Test
  void unknownCommandExits2() {
    int code = Cli.run(new String[] {"frobnicate"}, deps(p -> DoctorSnapshot.healthy()));
    assertThat(code).isEqualTo(2);
    assertThat(stderr()).contains("Unknown command: frobnicate");
  }

  // --- doctor ---------------------------------------------------------------------------------

  @Test
  void doctorHelpExits0() {
    assertThat(Cli.run(new String[] {"doctor", "--help"}, deps(p -> DoctorSnapshot.healthy())))
        .isZero();
    assertThat(stdout()).contains("Exit 0 = clean, 1 = findings, 2 = could not run.");
    outBytes.reset();
    assertThat(Cli.run(new String[] {"doctor", "-h"}, deps(p -> DoctorSnapshot.healthy())))
        .isZero();
    assertThat(stdout()).contains("Exit 0 = clean");
  }

  @Test
  void doctorUnknownOptionExits2() {
    int code = Cli.run(new String[] {"doctor", "--nope"}, deps(p -> DoctorSnapshot.healthy()));
    assertThat(code).isEqualTo(2);
    assertThat(stderr()).contains("Unknown doctor option: --nope");
  }

  @Test
  void doctorOnAHealthySnapshotExits0AndPrintsHumanText() {
    int code = Cli.run(new String[] {"doctor"}, deps(p -> DoctorSnapshot.healthy()));
    assertThat(code).isZero();
    assertThat(stdout()).contains("All checks passed.");
  }

  /**
   * A snapshot has to carry a real DEFECT to exit 1 — an absence is not one. A project that
   * declares nothing at all passes every check, so the dirty fixture here declares a JUnit outside
   * the supported range.
   */
  @Test
  void doctorJsonOnADirtySnapshotExits1AndPrintsJson() {
    DoctorSnapshot outOfRange =
        DoctorSnapshot.healthy().toBuilder()
            .buildFileContent("testImplementation(\"org.junit.jupiter:junit-jupiter:4.13.2\")")
            .build();
    int code = Cli.run(new String[] {"doctor", "--json"}, deps(p -> outOfRange));
    assertThat(code).isEqualTo(1);
    assertThat(stdout()).contains("\"findings\"").contains("\"exitCode\": 1");
  }

  @Test
  void doctorOnAProjectThatDeclaresNothingExits0() {
    int code = Cli.run(new String[] {"doctor"}, deps(p -> DoctorSnapshot.builder().build()));
    assertThat(code).isZero();
    assertThat(stdout()).contains("All checks passed.");
  }

  @Test
  void standardDepsUsesTheRealFilesystemAndStreams() {
    Cli.Deps standard = Cli.Deps.standard();
    assertThat(standard.cwd()).isAbsolute();
    assertThat(standard.out()).isSameAs(System.out);
    assertThat(standard.err()).isSameAs(System.err);
    assertThat(standard.openCarrier().apply(null).skills()).isNotEmpty();
  }

  /**
   * The real launcher feeds its OWN bundled carrier to the doctor, so {@code narrativetrace doctor}
   * in a project that never ran {@code init} can say the skills are missing instead of "cannot
   * tell". Only the standard dependencies do this; an injected snapshot function is whatever a test
   * wants.
   *
   * <p>Run from compiled classes rather than from the jar, the carrier has no archive whose name
   * could carry a version, so the coordinate it reports is honestly partial — the assertion here is
   * the part that holds either way, and the real jar's own coordinate is asserted by the build test
   * that runs {@code java -jar}.
   */
  @Test
  void standardDepsHandTheBundledCarrierToTheDoctor(@TempDir Path project) {
    DoctorSnapshot snapshot = Cli.Deps.standard().buildSnapshot().apply(project);

    assertThat(snapshot.carrierResolved()).isTrue();
    assertThat(snapshot.carrierCoordinate()).startsWith("ai.narrativetrace:");
    assertThat(snapshot.catalogueSkills()).isNotEmpty();
  }

  // --- doctor: the skills check, end to end through the launcher ------------------------------

  private static final String CARRIER = "ai.narrativetrace:narrativetrace-skills:1.2.3";
  private static final String OLDER = "ai.narrativetrace:narrativetrace-skills:0.0.1";

  private static DoctorSnapshot.Builder withSkills() {
    return DoctorSnapshot.healthy().toBuilder()
        .clearInstalledSkills()
        .carrier(CARRIER, List.of("narrativetrace-doctor", "add-narrative-tracing"));
  }

  private static InstalledSkill page(String name, InstalledSkill.Presence presence, String stamp) {
    return new InstalledSkill(SkillFlavour.AGENTS, name, presence, stamp, "page");
  }

  private int doctor(DoctorSnapshot snapshot) {
    return Cli.run(new String[] {"doctor"}, deps(p -> snapshot));
  }

  @Test
  void doctorPassesWhenEverySkillIsInstalledFromTheResolvedCarrier() {
    int code =
        doctor(
            withSkills()
                .addInstalledSkill(
                    page("narrativetrace-doctor", InstalledSkill.Presence.OURS, CARRIER))
                .addInstalledSkill(
                    page("add-narrative-tracing", InstalledSkill.Presence.OURS, CARRIER))
                .build());

    assertThat(code).isZero();
    assertThat(stdout()).contains("All checks passed.");
  }

  @Test
  void doctorFailsAndNamesTheInstallerWhenNoSkillIsInstalled() {
    assertThat(doctor(withSkills().build())).isEqualTo(1);
    assertThat(stdout())
        .contains("[FAIL] config.skills-installed")
        .contains("./gradlew narrativetraceInit --diff");
  }

  @Test
  void doctorFailsAndNamesBothCoordinatesWhenTheSkillsAreStale() {
    int code =
        doctor(
            withSkills()
                .addInstalledSkill(
                    page("narrativetrace-doctor", InstalledSkill.Presence.OURS, OLDER))
                .addInstalledSkill(
                    page("add-narrative-tracing", InstalledSkill.Presence.OURS, OLDER))
                .build());

    assertThat(code).isEqualTo(1);
    assertThat(stdout()).contains("installed from " + OLDER).contains("resolves " + CARRIER);
  }

  @Test
  void doctorFailsAndNamesTheMissingSkillWhenOnlySomeAreInstalled() {
    int code =
        doctor(
            withSkills()
                .addInstalledSkill(
                    page("narrativetrace-doctor", InstalledSkill.Presence.OURS, CARRIER))
                .build());

    assertThat(code).isEqualTo(1);
    assertThat(stdout()).contains("add-narrative-tracing is missing");
  }

  @Test
  void doctorReportsAForeignSkillDirectoryWithoutCountingIt() {
    int code =
        doctor(
            withSkills()
                .addInstalledSkill(
                    page("narrativetrace-doctor", InstalledSkill.Presence.OURS, CARRIER))
                .addInstalledSkill(
                    page("add-narrative-tracing", InstalledSkill.Presence.FOREIGN, ""))
                .build());

    assertThat(code).isEqualTo(1);
    assertThat(stdout()).contains("not ours");
  }

  @Test
  void doctorSaysItCannotTellWhenNoCarrierCouldBeResolved() {
    int code =
        doctor(
            DoctorSnapshot.healthy().toBuilder()
                .clearInstalledSkills()
                .carrier("", List.of())
                .build());

    assertThat(code).isZero();
    assertThat(stdout()).contains("All checks passed.");
  }

  @Test
  void doctorJsonCarriesTheSkillFieldForEveryFinding() {
    Cli.run(new String[] {"doctor", "--json"}, deps(p -> DoctorSnapshot.healthy()));
    assertThat(stdout())
        .contains("\"skill\": \"narrativetrace-doctor\"")
        .contains("\"skill\": null");
  }

  // --- init and uninstall ---------------------------------------------------------------------

  /** A project directory plus the fixture carrier, wired into one set of dependencies. */
  private Cli.Deps installerDeps(Path project, Path carrierParent) {
    Carrier carrier = TestCarriers.carrier(carrierParent);
    return new Cli.Deps(
        project, p -> DoctorSnapshot.healthy(), from -> carrier, () -> false, out, err);
  }

  private int init(Path project, Path carrierParent, String... flags) {
    return Cli.run(command("init", flags), installerDeps(project, carrierParent));
  }

  private int uninstall(Path project, String... flags) {
    return Cli.run(
        command("uninstall", flags),
        new Cli.Deps(
            project, p -> DoctorSnapshot.healthy(), from -> noCarrier(), () -> false, out, err));
  }

  private static String[] command(String verb, String... flags) {
    return Stream.concat(Stream.of(verb), Stream.of(flags)).toArray(String[]::new);
  }

  private static Path agentsPage(Path project) {
    return project.resolve(".agents/skills/" + TestCarriers.SKILL + "/SKILL.md");
  }

  @Test
  void initOnAFreshProjectWritesThePageAndTheSection(@TempDir Path dir) {
    int code = init(dir, dir.resolve("carrier"));

    assertThat(code).isZero();
    assertThat(agentsPage(dir)).exists();
    assertThat(dir.resolve("AGENTS.md")).exists();
    assertThat(stdout()).contains("applied");
  }

  @Test
  void initStampsEveryPageWithTheCarriersCoordinate(@TempDir Path dir) throws IOException {
    init(dir, dir.resolve("carrier"));

    assertThat(Files.readString(agentsPage(dir)))
        .contains("ai.narrativetrace:narrativetrace-skills:1.2.3");
  }

  @Test
  void initDryRunWritesNothingAndPrintsTheDiff(@TempDir Path dir) {
    int code = init(dir, dir.resolve("carrier"), "--dry-run");

    assertThat(code).isZero();
    assertThat(agentsPage(dir)).doesNotExist();
    assertThat(project(dir)).doesNotContainKey("AGENTS.md");
    assertThat(stdout()).contains("+++ b/AGENTS.md").contains("@@");
  }

  @Test
  void aSecondInitIsANoOp(@TempDir Path dir) {
    init(dir, dir.resolve("carrier"));
    outBytes.reset();

    int code = init(dir, dir.resolve("carrier"));

    assertThat(code).isZero();
    assertThat(stdout()).contains("0 applied");
  }

  @Test
  void initRefusesAnExistingAgentsMdAndNamesTheFlag(@TempDir Path dir) throws IOException {
    Files.writeString(dir.resolve("AGENTS.md"), "# Mine\n");

    int code = init(dir, dir.resolve("carrier"));

    assertThat(code).isEqualTo(1);
    assertThat(stdout()).contains("--write-existing");
    assertThat(Files.readString(dir.resolve("AGENTS.md"))).isEqualTo("# Mine\n");
  }

  @Test
  void initWithWriteExistingAppendsTheSection(@TempDir Path dir) throws IOException {
    Files.writeString(dir.resolve("AGENTS.md"), "# Mine\n");

    int code = init(dir, dir.resolve("carrier"), "--write-existing");

    assertThat(code).isZero();
    assertThat(Files.readString(dir.resolve("AGENTS.md")))
        .startsWith("# Mine\n")
        .contains("<!-- narrativetrace:start ");
  }

  @Test
  void initOnlySkillsLeavesTheSectionAlone(@TempDir Path dir) {
    int code = init(dir, dir.resolve("carrier"), "--only", "skills");

    assertThat(code).isZero();
    assertThat(agentsPage(dir)).exists();
    assertThat(dir.resolve("AGENTS.md")).doesNotExist();
  }

  @Test
  void initOnlyAgentsMdLeavesTheSkillsAlone(@TempDir Path dir) {
    int code = init(dir, dir.resolve("carrier"), "--only", "agents-md");

    assertThat(code).isZero();
    assertThat(agentsPage(dir)).doesNotExist();
    assertThat(dir.resolve("AGENTS.md")).exists();
  }

  @Test
  void initVendorClaudeWritesTheVendorCopyIntoAProjectThatShowsNoSignOfIt(@TempDir Path dir) {
    init(dir, dir.resolve("carrier"), "--vendor", "claude");

    assertThat(dir.resolve(".claude/skills/" + TestCarriers.SKILL + "/SKILL.md")).exists();
  }

  @Test
  void initVendorNoneSkipsTheVendorCopyEvenWhereItIsDetected(@TempDir Path dir) throws IOException {
    Files.createDirectories(dir.resolve(".claude"));

    init(dir, dir.resolve("carrier"), "--vendor", "none");

    assertThat(dir.resolve(".claude/skills/" + TestCarriers.SKILL + "/SKILL.md")).doesNotExist();
    assertThat(agentsPage(dir)).exists();
  }

  @Test
  void initForceOverwritesASkillDirectorySomebodyElseOwns(@TempDir Path dir) throws IOException {
    Path page = agentsPage(dir);
    Files.createDirectories(page.getParent());
    Files.writeString(page, "# theirs\n");

    assertThat(init(dir, dir.resolve("carrier"))).isEqualTo(1);
    assertThat(Files.readString(page)).isEqualTo("# theirs\n");
    outBytes.reset();

    assertThat(init(dir, dir.resolve("carrier"), "--force")).isZero();
    assertThat(Files.readString(page)).contains("agents body");
  }

  @Test
  void initJsonPrintsTheSameEnvelopeTheDoctorDoes(@TempDir Path dir) {
    int code = init(dir, dir.resolve("carrier"), "--json");

    assertThat(code).isZero();
    assertThat(stdout())
        .contains("\"carrier\": \"ai.narrativetrace:narrativetrace-skills:1.2.3\"")
        .contains("\"actions\"")
        .contains("\"status\": \"applied\"")
        .contains("\"exitCode\": 0");
  }

  @Test
  void initDryRunJsonReportsPlannedActionsAndExitsZeroDespiteARefusal(@TempDir Path dir)
      throws IOException {
    Files.writeString(dir.resolve("AGENTS.md"), "# Mine\n");

    int code = init(dir, dir.resolve("carrier"), "--dry-run", "--json");

    assertThat(code).isZero();
    assertThat(stdout()).contains("\"status\": \"refused\"").contains("\"exitCode\": 0");
  }

  @Test
  void initHelpExits0AndNamesEveryFlag() {
    int code = Cli.run(new String[] {"init", "--help"}, deps(p -> DoctorSnapshot.healthy()));

    assertThat(code).isZero();
    assertThat(stdout())
        .contains("--dry-run")
        .contains("--write-existing")
        .contains("--force")
        .contains("--only")
        .contains("--vendor")
        .contains("--from")
        .contains("--json");
  }

  @Test
  void uninstallHelpExits0() {
    assertThat(Cli.run(new String[] {"uninstall", "-h"}, deps(p -> DoctorSnapshot.healthy())))
        .isZero();
    assertThat(stdout()).contains("narrativetrace uninstall");
  }

  @Test
  void anUnknownInstallerOptionExits2AndPrintsTheVerbsUsage() {
    int code = Cli.run(new String[] {"init", "--nope"}, deps(p -> DoctorSnapshot.healthy()));

    assertThat(code).isEqualTo(2);
    assertThat(stderr()).contains("unknown option: \"--nope\"").contains("narrativetrace init");
  }

  @Test
  void aBadOptionValueExits2(@TempDir Path dir) {
    assertThat(init(dir, dir.resolve("carrier"), "--only", "everything")).isEqualTo(2);
    assertThat(stderr()).contains("--only takes skills or agents-md");
  }

  @Test
  void aCarrierThatCannotBeOpenedExits1AndPrintsTheFetchSnippet(@TempDir Path dir) {
    Cli.Deps deps =
        new Cli.Deps(
            dir,
            p -> DoctorSnapshot.healthy(),
            from -> {
              throw new IllegalArgumentException("no carrier at " + from);
            },
            () -> false,
            out,
            err);

    int code = Cli.run(new String[] {"init", "--from", "nowhere.jar"}, deps);

    assertThat(code).isEqualTo(1);
    assertThat(stderr()).contains("no carrier at nowhere.jar").contains("mvn dependency:copy");
    assertThat(project(dir)).isEmpty();
  }

  @Test
  void fromIsHandedToTheLocatorVerbatim(@TempDir Path dir) {
    var asked = new StringBuilder();
    Carrier carrier = TestCarriers.carrier(dir.resolve("carrier"));
    Cli.Deps deps =
        new Cli.Deps(
            dir,
            p -> DoctorSnapshot.healthy(),
            from -> {
              asked.append(from);
              return carrier;
            },
            () -> false,
            out,
            err);

    Cli.run(new String[] {"init", "--from=some.jar", "--dry-run"}, deps);

    assertThat(asked).hasToString("some.jar");
  }

  /**
   * The project walk is the second thing that can fail, after the carrier — a directory that is not
   * one. Exit 1, not 2: the command was typed correctly, it could not do the work.
   */
  @Test
  void aProjectDirectoryThatIsNotOneExits1(@TempDir Path dir) {
    int code = init(dir.resolve("gone"), dir.resolve("carrier"));

    assertThat(code).isEqualTo(1);
    assertThat(stderr()).contains("is not a directory");
  }

  @Test
  void uninstallNeverOpensACarrier(@TempDir Path dir) {
    init(dir, dir.resolve("carrier"));

    assertThat(uninstall(dir)).isZero();
  }

  @Test
  void uninstallAfterInitLeavesTheTreeByteIdentical(@TempDir Path dir) throws IOException {
    Files.writeString(dir.resolve("CLAUDE.md"), "# Claude\n");
    Files.writeString(dir.resolve("README.md"), "# Read me\n");
    Map<String, String> before = project(dir);

    init(dir, dir.resolve("carrier"), "--write-existing");
    uninstall(dir);

    assertThat(project(dir)).isEqualTo(before);
  }

  @Test
  void uninstallDryRunRemovesNothing(@TempDir Path dir) {
    init(dir, dir.resolve("carrier"));
    Map<String, String> installed = project(dir);

    int code = uninstall(dir, "--dry-run");

    assertThat(code).isZero();
    assertThat(project(dir)).isEqualTo(installed);
  }

  @Test
  void uninstallOnAProjectThatNeverRanInitDoesNothing(@TempDir Path dir) {
    int code = uninstall(dir);

    assertThat(code).isZero();
    assertThat(stdout()).contains("0 applied");
    assertThat(project(dir)).isEmpty();
  }

  /**
   * Every file under the project directory, by relative path — the carrier fixture lives in a
   * sibling directory on purpose, so it never shows up here.
   */
  private static Map<String, String> project(Path dir) {
    Map<String, String> tree = new LinkedHashMap<>();
    try (Stream<Path> files = Files.walk(dir)) {
      files
          .filter(Files::isRegularFile)
          .filter(file -> !dir.relativize(file).startsWith("carrier"))
          .sorted()
          .forEach(file -> tree.put(dir.relativize(file).toString(), read(file)));
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read the project tree at " + dir, e);
    }
    return tree;
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + file, e);
    }
  }
}
