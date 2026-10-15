/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The one safety property every repo-root nested build this replay starts must hold: it writes
 * nothing under a directory this test JVM loads classes from. It cannot be left to the two builds
 * agreeing on their inputs — they did not, and the disagreement (the outer build's {@code
 * -Pnarrativetrace.toolchain=21} against the nested build's inherited 17 pin) re-jarred
 * narrativetrace-tooling and narrativetrace-core underneath a live class loader, which is {@code
 * NoClassDefFoundError} for every class not yet loaded.
 *
 * <p>Pinned on the argument list rather than on a replayed run: a run only rewrites those jars when
 * the nested build happens to have something to do, so it is red on one toolchain and green on the
 * other. The isolation argument is there in every run or in none. {@code TierA2ReplayTest} is the
 * integration proof that the real nested build accepts it.
 */
class SkillReplayerBuildIsolationTest {

  private static final String ISOLATION = "-Pnarrativetrace.buildRoot=";

  private static List<Path> directoriesThisJvmLoadsClassesFrom() {
    return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
        .map(entry -> Path.of(entry).toAbsolutePath().normalize())
        .toList();
  }

  @Test
  void aReplayedRepoRootBuildIsIsolatedFromEveryDirectoryThisJvmLoadsClassesFrom() {
    List<String> arguments = SkillReplayer.repoRootGradleArguments(":sixty-seconds:test");

    assertThat(arguments)
        .as("the task stays first — the isolation is appended, never substituted")
        .startsWith(":sixty-seconds:test");
    assertThat(arguments.stream().filter(argument -> argument.startsWith(ISOLATION)))
        .as("every repo-root replay build must name its own build root")
        .hasSize(1);

    Path isolated =
        Path.of(
                arguments.stream()
                    .filter(argument -> argument.startsWith(ISOLATION))
                    .findFirst()
                    .orElseThrow()
                    .substring(ISOLATION.length()))
            .toAbsolutePath()
            .normalize();
    assertThat(directoriesThisJvmLoadsClassesFrom())
        .as(
            "a replayed build must write neither inside nor around what this JVM loads: %s",
            isolated)
        .allSatisfy(
            entry ->
                assertThat(entry.startsWith(isolated) || isolated.startsWith(entry)).isFalse());
  }

  @Test
  void appendsTheIsolationAfterEveryArgumentTheCallerAskedFor() {
    assertThat(
            SkillReplayer.repoRootGradleArguments(
                List.of(":sixty-seconds:test", "--tests", "com.example.orders.PlaceOrderFlowTest"),
                "/repo/build/replay-root"))
        .containsExactly(
            ":sixty-seconds:test",
            "--tests",
            "com.example.orders.PlaceOrderFlowTest",
            "-Pnarrativetrace.buildRoot=/repo/build/replay-root",
            "--project-cache-dir",
            "/repo/build/replay-root/project-cache");
  }

  /**
   * Moving the outputs is only half of isolation: a nested build that shares this checkout's {@code
   * .gradle} project cache shares the task history and the build-output-cleanup registry written
   * there, so the outer build's real output directories are "registered outputs nothing produces"
   * to it — and Gradle DELETES them as stale. Measured 2/2 on {@code
   * narrativetrace-skills-catalogue/build/classes/java/main}, emptied mid-test-task by a replay,
   * which is how the module's own ArchUnit rules came back "failed to check any classes".
   */
  @Test
  void aReplayedRepoRootBuildKeepsItsOwnTaskHistoryInsideTheIsolatedRoot() {
    assertThat(
            SkillReplayer.repoRootGradleArguments(
                List.of(":sixty-seconds:test"), "/repo/build/replay-root"))
        .as("a replayed build may neither read nor rewrite the outer build's task history")
        .containsSequence("--project-cache-dir", "/repo/build/replay-root/project-cache");
  }

  @Test
  void refusesToRunARepoRootBuildWithNoIsolatedBuildRoot() {
    assertThatIllegalStateException()
        .isThrownBy(
            () -> SkillReplayer.repoRootGradleArguments(List.of(":sixty-seconds:test"), null))
        .withMessageContaining("narrativetrace.replayBuildRoot");
  }

  @Test
  void refusesToRunARepoRootBuildWithABlankIsolatedBuildRoot() {
    assertThatIllegalStateException()
        .isThrownBy(
            () -> SkillReplayer.repoRootGradleArguments(List.of(":sixty-seconds:test"), "   "))
        .withMessageContaining("narrativetrace.replayBuildRoot");
  }
}
