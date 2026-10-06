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
package ai.narrativetrace.tooling.doctor;

import ai.narrativetrace.tooling.init.Carrier;
import ai.narrativetrace.tooling.init.ProjectStateReader;
import ai.narrativetrace.tooling.init.SkillEntry;
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
 *
 * <p><b>@llmNote</b> The agent-skill half of the snapshot is read by the INSTALLER's {@code
 * ProjectStateReader}, not by this walk: one reader of a project's skill directories, so the doctor
 * and {@code init} can never disagree about which page is ours. That reader is deliberately strict
 * where this one is best-effort, so its refusal is absorbed here rather than propagated.
 */
public final class SnapshotBuilder {

  /**
   * Directories never worth descending into: VCS metadata, dependency/IDE caches. Deliberately NOT
   * {@code build} — NarrativeTrace's own default rendered-output directory is {@code
   * build/narrativetrace}, so skipping the whole {@code build} tree by name would blind {@link
   * ai.narrativetrace.tooling.doctor.checks.ParameterArg0Check} to the one place it looks by
   * default.
   */
  private static final Set<String> EXCLUDED_DIR_NAMES =
      Set.of(".git", ".gradle", "node_modules", ".idea");

  private static final int DEFAULT_MAX_FILES = 20_000;

  /**
   * The doctor's OWN report, which sits in the same directory as the rendered output it reports on
   * and is not rendered output. Excluded by name, in whichever directory {@code outputDir} points
   * at: every report carries {@code arg0} inside the very messages that discuss {@code arg0}-style
   * parameter names, so reading one as rendered output made a SECOND doctor run report the FIRST
   * run's report as a project defect ({@code trap.parameter-arg0}). Found by a Tier B trial against
   * a project whose only sin was following the published prompt, which says to run the doctor.
   */
  private static final String DOCTOR_REPORT_NAME = "doctor-report.json";

  private static final Pattern DEPENDENCY_LINE =
      Pattern.compile("[\"']([a-zA-Z0-9_.\\-]+:[a-zA-Z0-9_.\\-]+:[a-zA-Z0-9_.\\-+]+)[\"']");

  private static final Pattern COMPILER_ARGS_PARAMETERS =
      Pattern.compile("compilerArgs\\.add\\(\\s*[\"']-parameters[\"']");

  /**
   * {@code junit.jupiter.extensions.autodetection.enabled=true} in {@code
   * junit-platform.properties} — JUnit Platform's own key=value format, so a trailing comment or
   * stray whitespace around {@code =} must not defeat the match.
   */
  private static final Pattern AUTODETECTION_PROPERTIES_LINE =
      Pattern.compile(
          "(?m)^\\s*junit\\.jupiter\\.extensions\\.autodetection\\.enabled\\s*=\\s*true\\s*$",
          Pattern.CASE_INSENSITIVE);

  /**
   * The same key set as a Gradle test-task JVM system property, Kotlin or Groovy DSL — {@code
   * systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")} or the single-quoted
   * Groovy equivalent.
   */
  private static final Pattern AUTODETECTION_SYSTEM_PROPERTY =
      Pattern.compile(
          "systemProperty\\(?\\s*[\"']junit\\.jupiter\\.extensions\\.autodetection\\.enabled[\"']"
              + "\\s*,\\s*[\"']true[\"']");

  private SnapshotBuilder() {}

  /** A snapshot of a project whose skills carrier the caller could not resolve. */
  public static DoctorSnapshot build(Path projectRoot) {
    return build(projectRoot, (Carrier) null);
  }

  /**
   * A snapshot of a project, plus the carrier its entry point resolved.
   *
   * @param carrier the resolved skills carrier, or {@code null} when it could not be resolved —
   *     being offline is not a defect, and the skills check answers "cannot tell" rather than
   *     failing a project for what nobody could look up
   */
  public static DoctorSnapshot build(Path projectRoot, Carrier carrier) {
    return build(projectRoot, carrier, DEFAULT_MAX_FILES);
  }

  static DoctorSnapshot build(Path projectRoot, Carrier carrier, int maxFiles) {
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
    boolean autodetectionEnabledViaPropertiesFile = walk(projectRoot, builder, maxFiles);

    String serviceFileText =
        readTextOrEmpty(
            projectRoot.resolve(
                "src/test/resources/META-INF/services/org.junit.jupiter.api.extension.Extension"));
    builder.extensionRegisteredViaServiceLoader(
        serviceFileText.contains("NarrativeTraceExtension")
            || autodetectionEnabledViaPropertiesFile
            || AUTODETECTION_SYSTEM_PROPERTY.matcher(buildFile).find());

    readAgentSkills(projectRoot, carrier, builder);
    return builder.build();
  }

  /**
   * The agent-skills half: which skill directories the project carries, and which catalogue it
   * should be carrying. A reader that refuses leaves the half empty — never a crashed doctor.
   */
  private static void readAgentSkills(
      Path projectRoot, Carrier carrier, DoctorSnapshot.Builder builder) {
    try {
      ProjectStateReader.read(projectRoot).installedSkills().forEach(builder::addInstalledSkill);
    } catch (RuntimeException e) {
      // Absent, unreadable, or not a directory at all: the doctor reports on a project, it does
      // not require one. The skills check then sees nothing installed, which is what is visible.
    }
    if (carrier != null) {
      builder.carrier(
          carrier.coordinate(), carrier.skills().stream().map(SkillEntry::name).toList());
    }
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

  private static boolean walk(Path projectRoot, DoctorSnapshot.Builder builder, int maxFiles) {
    if (!Files.isDirectory(projectRoot)) {
      return false;
    }
    int[] visited = {0};
    boolean[] extendWithFound = {false};
    boolean[] autodetectionEnabled = {false};
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
              if (!content.isEmpty()) {
                classifyFile(rel, name, content, builder, extendWithFound, autodetectionEnabled);
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      // A filesystem race mid-walk (a file removed between listing and reading) yields a
      // partial snapshot, not a crash — the same best-effort stance as visitFile above.
    }
    builder.extensionRegisteredViaExtendWith(extendWithFound[0]);
    return autodetectionEnabled[0];
  }

  /**
   * Buckets one non-empty file the walk visited: a source file (flagging {@code @ExtendWith}
   * registration along the way), a rendered output file, an approval baseline, or a {@code
   * junit-platform.properties} that turns on extension autodetection. Anything else is walked but
   * not otherwise recorded — including the doctor's own report, which lives among the rendered
   * output without being any (see {@link #DOCTOR_REPORT_NAME}).
   */
  private static void classifyFile(
      String rel,
      String name,
      String content,
      DoctorSnapshot.Builder builder,
      boolean[] extendWithFound,
      boolean[] autodetectionEnabled) {
    if (name.endsWith(".java") && rel.contains("src/")) {
      builder.putSourceFile(rel, content);
      if (content.contains("@ExtendWith") && content.contains("NarrativeTraceExtension")) {
        extendWithFound[0] = true;
      }
    } else if ((rel.contains("narrativetrace-output/") || rel.contains("build/narrativetrace/"))
        && !name.equals(DOCTOR_REPORT_NAME)) {
      builder.putOutputFile(rel, content);
    } else if (name.endsWith(".approved.nt") || name.endsWith(".received.nt")) {
      builder.putApprovalDirFile(rel, content);
    } else if (name.equals("junit-platform.properties")
        && AUTODETECTION_PROPERTIES_LINE.matcher(content).find()) {
      autodetectionEnabled[0] = true;
    }
  }
}
