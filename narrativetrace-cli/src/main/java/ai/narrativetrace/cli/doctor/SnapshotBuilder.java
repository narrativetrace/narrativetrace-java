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
package ai.narrativetrace.cli.doctor;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a {@link DoctorSnapshot} from a real project directory: read-only, zero network, a bounded
 * walk (a cap on files visited degrades a huge or pathological tree to a partial scan rather than a
 * hang — the same shape as the TypeScript reference's {@code environment.ts}). Every read is
 * best-effort: a file that vanishes or cannot be read mid-scan is skipped, never a crash — the
 * doctor's job is to report on a project, not to require a perfectly quiescent one.
 */
public final class SnapshotBuilder {

  /**
   * Directories never worth descending into: VCS metadata, dependency/IDE caches. Deliberately NOT
   * {@code build} — NarrativeTrace's own default rendered-output directory is {@code
   * build/narrativetrace}, so skipping the whole {@code build} tree by name would blind {@link
   * ai.narrativetrace.cli.doctor.checks.ParameterArg0Check} to the one place it looks by default.
   */
  private static final Set<String> EXCLUDED_DIR_NAMES =
      Set.of(".git", ".gradle", "node_modules", ".idea");

  private static final int DEFAULT_MAX_FILES = 20_000;

  private static final Pattern DEPENDENCY_LINE =
      Pattern.compile("[\"']([a-zA-Z0-9_.\\-]+:[a-zA-Z0-9_.\\-]+:[a-zA-Z0-9_.\\-+]+)[\"']");

  private static final Pattern COMPILER_ARGS_PARAMETERS =
      Pattern.compile("compilerArgs\\.add\\(\\s*[\"']-parameters[\"']");

  private SnapshotBuilder() {}

  public static DoctorSnapshot build(Path projectRoot) {
    return build(projectRoot, DEFAULT_MAX_FILES);
  }

  static DoctorSnapshot build(Path projectRoot, int maxFiles) {
    DoctorSnapshot.Builder builder =
        DoctorSnapshot.builder().runningJavaVersion(System.getProperty("java.version", "unknown"));

    System.getenv().forEach(builder::putEnv);
    System.getProperties()
        .forEach((k, v) -> builder.putSystemProperty(String.valueOf(k), String.valueOf(v)));

    String buildFile = readTextOrEmpty(projectRoot.resolve("build.gradle.kts"));
    if (buildFile.isEmpty()) {
      buildFile = readTextOrEmpty(projectRoot.resolve("build.gradle"));
    }
    builder.buildFileContent(buildFile);
    Matcher dep = DEPENDENCY_LINE.matcher(buildFile);
    while (dep.find()) {
      builder.addDependencyCoordinate(dep.group(1));
    }
    builder.launcherOnTestRuntimeOnly(declaresLauncherOnTestRuntimeOnly(buildFile));
    builder.compilerArgsDeclareParameters(COMPILER_ARGS_PARAMETERS.matcher(buildFile).find());

    readGradleProperties(projectRoot, builder);
    walk(projectRoot, builder, maxFiles);

    String serviceFileText =
        readTextOrEmpty(
            projectRoot.resolve(
                "src/test/resources/META-INF/services/org.junit.jupiter.api.extension.Extension"));
    builder.extensionRegisteredViaServiceLoader(
        serviceFileText.contains("NarrativeTraceExtension"));

    return builder.build();
  }

  private static String readTextOrEmpty(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      return "";
    }
  }

  /**
   * Plain substring scan rather than a regex: a {@code testRuntimeOnly(...)}/{@code
   * junit-platform-launcher} pairing spans at most one Gradle dependency declaration, so a per-line
   * "both words present" check is exact for real build files without inviting a
   * catastrophic-backtracking-shaped pattern for a static analyzer to flag.
   */
  private static boolean declaresLauncherOnTestRuntimeOnly(String buildFile) {
    return buildFile
        .lines()
        .anyMatch(
            line -> line.contains("testRuntimeOnly") && line.contains("junit-platform-launcher"));
  }

  private static void readGradleProperties(Path projectRoot, DoctorSnapshot.Builder builder) {
    String text = readTextOrEmpty(projectRoot.resolve("gradle.properties"));
    for (String line : text.split("\\R")) {
      String trimmed = line.strip();
      if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
        continue;
      }
      int eq = trimmed.indexOf('=');
      if (eq <= 0) {
        continue;
      }
      builder.putGradleProperty(
          trimmed.substring(0, eq).strip(), trimmed.substring(eq + 1).strip());
    }
  }

  private static void walk(Path projectRoot, DoctorSnapshot.Builder builder, int maxFiles) {
    if (!Files.isDirectory(projectRoot)) {
      return;
    }
    int[] visited = {0};
    boolean[] extendWithFound = {false};
    try {
      Files.walkFileTree(
          projectRoot,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
              if (visited[0] >= maxFiles) {
                return FileVisitResult.TERMINATE;
              }
              if (EXCLUDED_DIR_NAMES.contains(dir.getFileName().toString())) {
                return FileVisitResult.SKIP_SUBTREE;
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              if (visited[0] >= maxFiles) {
                return FileVisitResult.TERMINATE;
              }
              visited[0]++;
              String rel = projectRoot.relativize(file).toString().replace('\\', '/');
              String name = file.getFileName().toString();
              String content = readTextOrEmpty(file);
              if (content.isEmpty()) {
                return FileVisitResult.CONTINUE;
              }
              if (name.endsWith(".java") && rel.contains("src/")) {
                builder.putSourceFile(rel, content);
                if (content.contains("@ExtendWith")
                    && content.contains("NarrativeTraceExtension")) {
                  extendWithFound[0] = true;
                }
              } else if (rel.contains("narrativetrace-output/")
                  || rel.contains("build/narrativetrace/")) {
                builder.putOutputFile(rel, content);
              } else if (name.endsWith(".approved.nt") || name.endsWith(".received.nt")) {
                builder.putApprovalDirFile(rel, content);
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      // A filesystem race mid-walk (a file removed between listing and reading) yields a
      // partial snapshot, not a crash — the same best-effort stance as visitFile above.
    }
    builder.extensionRegisteredViaExtendWith(extendWithFound[0]);
  }
}
