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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Feature-combination coverage for {@link Cli} beyond {@code CliTest}: a global-looking flag
 * arriving before any command has been read, {@code --only agents-md} paired with {@code uninstall}
 * rather than {@code init}, and {@code --force} paired with {@code --vendor claude}.
 */
class CliAdversarialM3Test {

  private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
  private final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
  private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

  private Cli.Deps deps() {
    return new Cli.Deps(
        Path.of("."), p -> DoctorSnapshot.healthy(), from -> noCarrier(), () -> false, out, err);
  }

  private static Carrier noCarrier() {
    throw new IllegalStateException("this command must not open a carrier");
  }

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
  void aTopLevelFlagBeforeAnyCommandIsReportedAsAnUnknownCommand() {
    int code = Cli.run(new String[] {"--json"}, deps());

    assertThat(code).isEqualTo(2);
    assertThat(errBytes.toString(StandardCharsets.UTF_8)).contains("Unknown command: --json");
  }

  @Test
  void uninstallOnlyAgentsMdRemovesTheSectionButLeavesTheSkillDirectory(@TempDir Path dir) {
    init(dir, dir.resolve("carrier"));
    outBytes.reset();

    int code = uninstall(dir, "--only", "agents-md");

    assertThat(code).isZero();
    assertThat(dir.resolve("AGENTS.md")).doesNotExist();
    assertThat(agentsPage(dir)).exists();
  }

  @Test
  void forceAndVendorClaudeTogetherOverwriteAForeignVendorSkillDirectory(@TempDir Path dir)
      throws IOException {
    Path vendorPage = dir.resolve(".claude/skills/" + TestCarriers.SKILL + "/SKILL.md");
    Files.createDirectories(vendorPage.getParent());
    Files.writeString(vendorPage, "# theirs\n");

    int refused = init(dir, dir.resolve("carrier"), "--vendor", "claude");

    assertThat(refused).isEqualTo(1);
    assertThat(Files.readString(vendorPage)).isEqualTo("# theirs\n");
    outBytes.reset();

    int code = init(dir, dir.resolve("carrier"), "--vendor", "claude", "--force");

    assertThat(code).isZero();
    assertThat(Files.readString(vendorPage)).contains("claude body");
  }
}
