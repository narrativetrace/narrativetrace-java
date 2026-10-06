/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.evals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;

/**
 * INTENT: a throwaway vendor-tool configuration a trial owns, in BOTH directions.
 *
 * <p>Outward: a marketplace and a plugin are USER-level state, the only scope the documentation
 * describes, so a registry trial's installs land in the trial's own directory and never in the
 * configuration of the person running it.
 *
 * <p>Inward, and just as load-bearing: no trial inherits that person's configuration either. See
 * {@link #agentConfigEnv} for what went wrong before that held.
 *
 * <p>Two things stay deliberately OUTSIDE the isolation. {@code HOME} is untouched: it is where the
 * Gradle cache and the toolchain live, and a trial that moved it would spend its first minutes
 * re-downloading a distribution instead of measuring a skill. And the subscription LOGIN is copied
 * in, because a fresh configuration is a logged-out one — the agent CLI answers "Not logged in" and
 * the trial would measure the harness. Nothing else of the real configuration is read, and nothing
 * at all is written back to it.
 *
 * @llmNote The login file is a secret. It is copied owner-only into a directory the runner deletes
 *     when the trial ends, and it is the only file this class ever reads out of the real
 *     configuration — never the history, the sessions or the projects beside it.
 */
public final class IsolatedAgentConfig {

  /** The vendor CLI's own configuration-directory override. */
  private static final String CONFIG_DIR_VARIABLE = "CLAUDE_CONFIG_DIR";

  /** The subscription login, the one file a fresh configuration cannot do without. */
  private static final String LOGIN_FILE = ".credentials.json";

  private IsolatedAgentConfig() {}

  /** The isolated configuration directory inside a trial's work directory. */
  public static Path configDir(Path workDir) {
    return workDir.resolve("config");
  }

  /** Where the package runner caches its downloads — the ambient one may not be writable. */
  public static Path npmCacheDir(Path workDir) {
    return workDir.resolve("npm-cache");
  }

  /**
   * The isolated configuration, for EVERY trial — the one entry that keeps a trial from inheriting
   * the configuration of the person running it.
   *
   * <p>This is a measurement property, not a hygiene one. A trial driven against the operator's own
   * configuration is driven against the operator's own skills: the first feedback trial's {@code
   * system/init} event listed forty skills and thirty tools belonging to this machine, with the
   * skill under test one line among them. "A preset narrower than its prompt measures the sandbox"
   * has a mirror image, and this is it — a CONFIGURATION wider than the product measures the
   * laptop, and a red row would not reproduce anywhere else.
   */
  public static Map<String, String> agentConfigEnv(Path workDir) {
    return Map.of(CONFIG_DIR_VARIABLE, configDir(workDir).toString());
  }

  /**
   * The above, plus what only a REGISTRY trial needs: a writable package cache and a best-effort
   * telemetry opt-out for the registry tool that has one.
   */
  public static Map<String, String> env(Path workDir) {
    return Map.of(
        CONFIG_DIR_VARIABLE,
        agentConfigEnv(workDir).get(CONFIG_DIR_VARIABLE),
        "npm_config_cache",
        npmCacheDir(workDir).toString(),
        "DO_NOT_TRACK",
        "1");
  }

  /**
   * Creates the isolated configuration directory and copies the subscription login into it when
   * {@code realConfigDir} has one.
   *
   * @return whether a login was found and copied. A caller SAYS which it was: a trial with no login
   *     does not fail here, it fails three steps later when the agent answers "Not logged in", and
   *     that is a long way from the cause.
   * @sideEffects creates {@link #configDir(Path)} and the package cache directory; writes one
   *     owner-only copy of the login file there. Reads exactly one file of {@code realConfigDir}
   *     and writes none.
   */
  public static boolean seedLogin(Path realConfigDir, Path workDir) throws IOException {
    Path isolated = Files.createDirectories(configDir(workDir));
    Files.createDirectories(npmCacheDir(workDir));
    Path login = realConfigDir.resolve(LOGIN_FILE);
    if (!Files.isRegularFile(login)) {
      return false;
    }
    Path copy = isolated.resolve(LOGIN_FILE);
    Files.copy(login, copy, StandardCopyOption.REPLACE_EXISTING);
    ownerOnly(copy);
    return true;
  }

  /** The configuration directory the ambient environment points the vendor CLI at. */
  public static Path realConfigDir() {
    return realConfigDir(
        System.getenv(CONFIG_DIR_VARIABLE), System.getenv("HOME"), System.getProperty("user.home"));
  }

  /**
   * The override when one is set; otherwise the conventional directory under the home the TOOLS
   * use.
   *
   * @llmNote {@code HOME} is asked before the JVM's {@code user.home} on purpose: every CLI this
   *     harness drives reads the environment variable, and a container whose uid has no passwd
   *     entry hands the JVM {@code user.home=?} — a path with no login file under it, which turns
   *     into a logged-out agent two steps later.
   */
  static Path realConfigDir(String configDirVariable, String homeVariable, String userHome) {
    if (configDirVariable != null && !configDirVariable.isBlank()) {
      return Path.of(configDirVariable);
    }
    String home = homeVariable == null || homeVariable.isBlank() ? userHome : homeVariable;
    return Path.of(home).resolve(".claude");
  }

  /**
   * The copy is a credential, so it is readable by its owner and nobody else. Best effort by
   * necessity: a filesystem with no POSIX permission view is not a reason to refuse a trial, and
   * the directory it sits in is deleted when the trial ends either way.
   */
  private static void ownerOnly(Path path) throws IOException {
    if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    }
  }
}
