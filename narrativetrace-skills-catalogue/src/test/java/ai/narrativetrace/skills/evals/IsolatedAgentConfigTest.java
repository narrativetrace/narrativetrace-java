/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A registry case installs a plugin and a marketplace, which are USER-level state. The one place
 * they may land is a throwaway configuration directory the trial owns — never the configuration the
 * person running the trial works in.
 */
class IsolatedAgentConfigTest {

  @Test
  void pointsEveryVendorToolAtTheTrialsOwnDirectoriesAndLeavesHomeAlone(@TempDir Path work) {
    Map<String, String> env = IsolatedAgentConfig.env(work);

    assertThat(env)
        .containsEntry("CLAUDE_CONFIG_DIR", work.resolve("config").toString())
        .containsEntry("npm_config_cache", work.resolve("npm-cache").toString());
    assertThat(env)
        .as(
            "HOME is where the Gradle cache lives — a trial that moved it would re-download the"
                + " distribution and every dependency before the agent did anything")
        .doesNotContainKey("HOME");
  }

  @Test
  void seedsTheSubscriptionLoginAndNothingElseOfTheRealConfiguration(@TempDir Path tempDir)
      throws Exception {
    Path real = Files.createDirectories(tempDir.resolve("real"));
    Files.writeString(real.resolve(".credentials.json"), "{\"token\":\"t\"}");
    Files.writeString(real.resolve("history.jsonl"), "somebody's shell history");
    Files.createDirectories(real.resolve("projects/other"));
    Path work = Files.createDirectories(tempDir.resolve("work"));

    IsolatedAgentConfig.seedLogin(real, work);

    Path isolated = IsolatedAgentConfig.configDir(work);
    assertThat(isolated.resolve(".credentials.json")).hasContent("{\"token\":\"t\"}");
    assertThat(isolated.resolve("history.jsonl")).doesNotExist();
    assertThat(isolated.resolve("projects")).doesNotExist();
    assertThat(real.resolve(".credentials.json"))
        .as("the real configuration is READ and never written")
        .hasContent("{\"token\":\"t\"}");
  }

  /**
   * A configuration with no login file is the ordinary state of a machine that authenticates some
   * other way. Seeding must then be a no-op, not a failure: whether the agent can start is the
   * agent's own answer, and a crash here would blame the harness for it.
   */
  @Test
  void seedingIsANoOpWhenTheRealConfigurationCarriesNoLoginFile(@TempDir Path tempDir)
      throws Exception {
    Path real = Files.createDirectories(tempDir.resolve("real"));
    Path work = Files.createDirectories(tempDir.resolve("work"));

    IsolatedAgentConfig.seedLogin(real, work);

    assertThat(IsolatedAgentConfig.configDir(work)).isDirectory();
    assertThat(IsolatedAgentConfig.configDir(work).resolve(".credentials.json")).doesNotExist();
  }

  /** The copy is a credential: on a POSIX filesystem nobody but its owner may read it. */
  @Test
  void writesTheCopiedLoginOwnerOnly(@TempDir Path tempDir) throws Exception {
    Path real = Files.createDirectories(tempDir.resolve("real"));
    Files.writeString(real.resolve(".credentials.json"), "{\"token\":\"t\"}");
    Path work = Files.createDirectories(tempDir.resolve("work"));

    IsolatedAgentConfig.seedLogin(real, work);

    Path copy = IsolatedAgentConfig.configDir(work).resolve(".credentials.json");
    if (copy.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      assertThat(Files.getPosixFilePermissions(copy))
          .isEqualTo(PosixFilePermissions.fromString("rw-------"));
    }
  }

  @Test
  void resolvesTheRealConfigurationFromTheEnvironmentFirstAndTheHomeDirectorySecond() {
    assertThat(
            IsolatedAgentConfig.realConfigDir("/elsewhere/cfg", "/home/someone", "/home/someone"))
        .isEqualTo(Path.of("/elsewhere/cfg"));
    assertThat(IsolatedAgentConfig.realConfigDir(null, "/home/someone", "/home/someone"))
        .isEqualTo(Path.of("/home/someone/.claude"));
    assertThat(IsolatedAgentConfig.realConfigDir("  ", "/home/someone", "/home/someone"))
        .isEqualTo(Path.of("/home/someone/.claude"));
  }

  /**
   * `HOME` is asked BEFORE `user.home`, and it cost a trial to learn why: a container whose uid has
   * no passwd entry hands the JVM {@code user.home=?}, so the login was looked for under
   * `?/.claude`, nothing was found, seeding was the documented no-op — and the agent then answered
   * "Not logged in" and crashed the trial. Every tool this harness drives reads `HOME`, so this
   * does too.
   */
  @Test
  void prefersTheHomeEnvironmentVariableOverAJvmUserHomeThatCannotBeResolved() {
    assertThat(IsolatedAgentConfig.realConfigDir(null, "/home/dev", "?"))
        .isEqualTo(Path.of("/home/dev/.claude"));
    assertThat(IsolatedAgentConfig.realConfigDir(null, null, "/home/someone"))
        .isEqualTo(Path.of("/home/someone/.claude"));
    assertThat(IsolatedAgentConfig.realConfigDir(null, "", "/home/someone"))
        .isEqualTo(Path.of("/home/someone/.claude"));
  }

  /** Whether a login was found is reported, because "no login" ends a trial three steps later. */
  @Test
  void saysWhetherItFoundALoginToSeed(@TempDir Path tempDir) throws Exception {
    Path real = Files.createDirectories(tempDir.resolve("real"));
    Path work = Files.createDirectories(tempDir.resolve("work"));
    assertThat(IsolatedAgentConfig.seedLogin(real, work)).isFalse();

    Files.writeString(real.resolve(".credentials.json"), "{\"token\":\"t\"}");
    assertThat(IsolatedAgentConfig.seedLogin(real, work)).isTrue();
  }
}
