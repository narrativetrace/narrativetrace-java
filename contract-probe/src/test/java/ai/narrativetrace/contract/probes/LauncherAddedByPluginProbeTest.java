/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract.probes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * B-65: {@link LauncherAddedByPluginProbe} used to return {@code "false"} for ANY non-zero exit —
 * "the plugin id could not be resolved at all" (the Plugin Portal proxy answering 404 for a marker
 * still awaiting Gradle's approval, while Central already serves it) and "Gradle resolved the
 * plugin but the launcher is not on the classpath" were indistinguishable (release rule 2). A fake
 * {@code gradlew} script on {@code gradlewPath} covers all three outcomes this probe must tell
 * apart: {@code "true"}, {@code "false"} (Gradle succeeded, no launcher on the resolved classpath),
 * and an error verdict naming the first Gradle error line when the build itself fails — never
 * folded into {@code "false"}.
 */
class LauncherAddedByPluginProbeTest {

  @Test
  void trueWhenGradleSucceedsAndTheLauncherIsOnTheClasspath() throws IOException {
    String gradlew = fakeGradlew(0, "org.junit.platform:junit-platform-launcher:1.11.4\n");
    assertEquals("true", LauncherAddedByPluginProbe.observe("0.2.4", gradlew));
  }

  @Test
  void falseWhenGradleSucceedsButNoLauncherIsResolved() throws IOException {
    String gradlew = fakeGradlew(0, "org.junit.jupiter:junit-jupiter-api:5.11.4\n");
    assertEquals("false", LauncherAddedByPluginProbe.observe("0.2.4", gradlew));
  }

  @Test
  void errorNamesTheFirstGradleErrorLineWhenTheBuildFails() throws IOException {
    String gradlew =
        fakeGradlew(
            1,
            "> Task :dependencies\n"
                + "\n"
                + "FAILURE: Build failed with an exception.\n"
                + "\n"
                + "* What went wrong:\n"
                + "Plugin [id: 'ai.narrativetrace', version: '0.2.4'] was not found in any of the "
                + "following sources:\n"
                + "\n"
                + "* Try:\n"
                + "Run with --stacktrace option to get the stack trace.\n");
    String observed = LauncherAddedByPluginProbe.observe("0.2.4", gradlew);
    assertNotEquals("false", observed, "a build failure must never read as \"false\"");
    assertTrue(observed.startsWith("error: "), "expected an error verdict, got: " + observed);
    assertTrue(
        observed.contains("Plugin [id: 'ai.narrativetrace', version: '0.2.4'] was not found"),
        "expected the first Gradle error line, got: " + observed);
  }

  private static String fakeGradlew(int exitCode, String stdout) throws IOException {
    Path script = Files.createTempFile("fake-gradlew", ".sh");
    Files.writeString(
        script, "#!/bin/sh\ncat <<'SCRIPT_EOF'\n" + stdout + "SCRIPT_EOF\nexit " + exitCode + "\n");
    Files.setPosixFilePermissions(
        script,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE));
    return script.toAbsolutePath().toString();
  }
}
