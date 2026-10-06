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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Builds a {@link ProjectState} from a real project directory: read-only, zero network, a bounded
 * listing of each install root.
 *
 * <p>INTENT: the installer's single reader. Every file the planner reasons about is read here,
 * once, so nothing downstream touches the project until the executor writes.
 *
 * <p><b>@llmNote</b> Unlike the doctor's snapshot, this reader is NOT best-effort. A file that
 * exists but cannot be read is a hard failure, because "unreadable" and "absent" lead to opposite
 * plans — absent means create, and creating over a file we could not read would destroy it.
 *
 * <p><b>@sideEffects</b> Reads. Never writes, never creates a directory.
 */
public final class ProjectStateReader {

  /** Vendor rule files whose existing managed block is kept up to date, never created. */
  private static final List<String> RULE_FILES =
      List.of(".cursorrules", ".github/copilot-instructions.md");

  private static final List<String> BUILD_FILES = List.of("build.gradle.kts", "build.gradle");

  private static final List<String> SETTINGS_FILES =
      List.of("settings.gradle.kts", "settings.gradle");

  private static final String OUTPUT_DIRECTORY_KEY = "narrativetrace.outputDir";

  /** {@code systemProperty("narrativetrace.outputDir", "out")}, Kotlin or Groovy DSL. */
  private static final Pattern OUTPUT_DIRECTORY_IN_BUILD_FILE =
      Pattern.compile(
          "systemProperty\\(?\\s*[\"']"
              + Pattern.quote(OUTPUT_DIRECTORY_KEY)
              + "[\"']"
              + "\\s*,\\s*[\"']([^\"']+)[\"']");

  /** A listing cap, so a pathological tree degrades to a partial read rather than to a hang. */
  private static final int DEFAULT_MAX_SKILL_DIRECTORIES = 200;

  private ProjectStateReader() {}

  /** Reads a project directory. */
  public static ProjectState read(Path projectDirectory) {
    return read(projectDirectory, DEFAULT_MAX_SKILL_DIRECTORIES);
  }

  static ProjectState read(Path projectDirectory, int maxSkillDirectories) {
    if (projectDirectory == null) {
      throw new IllegalArgumentException("a project directory must be given");
    }
    if (!Files.isDirectory(projectDirectory)) {
      throw new IllegalArgumentException(projectDirectory + " is not a directory");
    }
    ProjectState.Builder builder =
        ProjectState.builder()
            .claudeDirectory(Files.isDirectory(projectDirectory.resolve(".claude")))
            .gradleProject(isGradleProject(projectDirectory))
            .outputDirectory(outputDirectory(projectDirectory));
    textOf(projectDirectory.resolve("AGENTS.md")).ifPresent(builder::agentsMd);
    textOf(projectDirectory.resolve("CLAUDE.md")).ifPresent(builder::claudeMd);
    readRuleFiles(projectDirectory, builder);
    for (SkillFlavour flavour : SkillFlavour.values()) {
      readInstallRoot(projectDirectory, flavour, maxSkillDirectories, builder);
    }
    return builder.build();
  }

  private static void readRuleFiles(Path projectDirectory, ProjectState.Builder builder) {
    for (String relative : RULE_FILES) {
      textOf(projectDirectory.resolve(relative))
          .filter(ProjectStateReader::carriesMarkers)
          .ifPresent(content -> builder.markedRuleFile(relative, content));
    }
  }

  private static boolean carriesMarkers(String content) {
    MarkedBlock.Scan scan = MarkedBlock.scan(content);
    return !scan.regions().isEmpty() || !scan.problems().isEmpty();
  }

  private static void readInstallRoot(
      Path projectDirectory, SkillFlavour flavour, int max, ProjectState.Builder builder) {
    Path root = projectDirectory.resolve(flavour.installRoot());
    Optional<String> linked = linkTarget(root);
    if (linked.isPresent()) {
      builder.linkedInstallRoot(flavour, linked.get());
      return;
    }
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    for (Path child : children(root, max)) {
      builder.addInstalledSkill(installedSkill(projectDirectory, flavour, child));
    }
  }

  private static List<Path> children(Path root, int max) {
    try (Stream<Path> entries = Files.list(root)) {
      return entries
          .sorted(Comparator.comparing(path -> path.getFileName().toString()))
          .limit(max)
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("cannot list " + root, e);
    }
  }

  /**
   * What sits at one skill's path, WITHOUT following a link on the way. A link is reported as one,
   * never resolved into "a directory of ours": writing through it would land in whatever it points
   * at, and after {@code npx skills add} that is the other flavour's page.
   */
  private static InstalledSkill installedSkill(
      Path projectDirectory, SkillFlavour flavour, Path directory) {
    String name = directory.getFileName().toString();
    Optional<String> toDirectory = linkTarget(directory);
    if (toDirectory.isPresent()) {
      return linked(
          projectDirectory,
          flavour,
          name,
          InstalledSkill.Presence.LINKED_DIRECTORY,
          toDirectory.get());
    }
    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
      return new InstalledSkill(flavour, name, InstalledSkill.Presence.NOT_A_DIRECTORY, "", "");
    }
    Optional<String> toPage = linkTarget(directory.resolve(InstalledSkill.PAGE));
    return toPage.isPresent()
        ? linked(projectDirectory, flavour, name, InstalledSkill.Presence.LINKED_PAGE, toPage.get())
        : pageOf(flavour, name, directory.resolve(InstalledSkill.PAGE));
  }

  /**
   * A real directory: ours when its page carries the provenance line, somebody else's otherwise.
   */
  private static InstalledSkill pageOf(SkillFlavour flavour, String name, Path pageFile) {
    String page = textOf(pageFile).orElse("");
    return Provenance.coordinateIn(page)
        .map(
            coordinate ->
                new InstalledSkill(flavour, name, InstalledSkill.Presence.OURS, coordinate, page))
        .orElseGet(
            () -> new InstalledSkill(flavour, name, InstalledSkill.Presence.FOREIGN, "", page));
  }

  /**
   * A skill behind a link: what the link says, and the page it reaches — read only when the link
   * really resolves INSIDE this project, because nothing outside it is ours to stamp or to remove.
   */
  private static InstalledSkill linked(
      Path projectDirectory,
      SkillFlavour flavour,
      String name,
      InstalledSkill.Presence presence,
      String target) {
    Path page =
        projectDirectory.resolve(flavour.installRoot()).resolve(name).resolve(InstalledSkill.PAGE);
    String body = insideProject(projectDirectory, page) ? textOf(page).orElse("") : "";
    return new InstalledSkill(flavour, name, presence, "", body, target);
  }

  /**
   * Whether a path REALLY resolves inside the project. A link that resolves to nothing — dangling,
   * or a chain the filesystem will not follow — answers no, which is the same answer a link out of
   * the project gets: the installer treats both as reaching no page at all.
   */
  private static boolean insideProject(Path projectDirectory, Path path) {
    try {
      return path.toRealPath().startsWith(projectDirectory.toRealPath());
    } catch (IOException e) {
      return false;
    }
  }

  /** What a symbolic link points at, or empty when the path is not one. */
  private static Optional<String> linkTarget(Path path) {
    if (!Files.isSymbolicLink(path)) {
      return Optional.empty();
    }
    try {
      return Optional.of(Files.readSymbolicLink(path).toString());
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read the symbolic link " + path, e);
    }
  }

  private static String outputDirectory(Path projectDirectory) {
    Optional<String> fromProperties =
        textOf(projectDirectory.resolve("gradle.properties")).flatMap(ProjectStateReader::property);
    if (fromProperties.isPresent()) {
      return fromProperties.get();
    }
    for (String buildFile : BUILD_FILES) {
      Optional<String> fromBuildFile =
          textOf(projectDirectory.resolve(buildFile)).flatMap(ProjectStateReader::inBuildFile);
      if (fromBuildFile.isPresent()) {
        return fromBuildFile.get();
      }
    }
    return ProjectState.DEFAULT_OUTPUT_DIRECTORY;
  }

  private static Optional<String> property(String properties) {
    return properties
        .lines()
        .map(String::trim)
        .filter(line -> line.startsWith(OUTPUT_DIRECTORY_KEY + "="))
        .map(line -> line.substring(line.indexOf('=') + 1).trim())
        .filter(value -> !value.isEmpty())
        .findFirst();
  }

  private static Optional<String> inBuildFile(String buildFile) {
    Matcher matcher = OUTPUT_DIRECTORY_IN_BUILD_FILE.matcher(buildFile);
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  private static boolean isGradleProject(Path projectDirectory) {
    return Stream.concat(BUILD_FILES.stream(), SETTINGS_FILES.stream())
        .anyMatch(name -> Files.isRegularFile(projectDirectory.resolve(name)));
  }

  /**
   * The file's text, or empty when it does not exist. An unreadable file is a failure, not empty.
   */
  private static Optional<String> textOf(Path file) {
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      return Optional.of(Files.readString(file));
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + file, e);
    }
  }
}
