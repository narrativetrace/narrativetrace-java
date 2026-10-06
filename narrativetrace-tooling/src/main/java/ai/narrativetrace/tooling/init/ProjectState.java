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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A read-only snapshot of a consumer project: everything the installer's decisions depend on, read
 * once and never read again.
 *
 * <p>INTENT: makes the planner a pure function. Every "does this file exist", "is this directory
 * ours", "where do traces land" question is answered here, so a plan can be computed, printed as a
 * diff, reviewed, and only then applied — with no chance of the answer changing between the diff
 * and the write.
 *
 * <p>Built two ways: {@link ProjectStateReader} walks a real directory; a test builds one directly
 * with {@link #builder()}, which is why every planner case is a unit test with no disk at all.
 *
 * <p><b>@llmNote</b> Contents are kept byte for byte — line endings, byte-order mark, missing final
 * newline and all. The planner's edits are expressed against exactly these bytes.
 */
public final class ProjectState {

  /** Where rendered traces land when a project says nothing else. */
  public static final String DEFAULT_OUTPUT_DIRECTORY = "build/narrativetrace";

  private final String agentsMd;
  private final String claudeMd;
  private final boolean claudeDirectory;
  private final List<InstalledSkill> installedSkills;
  private final Map<SkillFlavour, String> linkedInstallRoots;
  private final Map<String, String> markedRuleFiles;
  private final String outputDirectory;
  private final boolean gradleProject;

  private ProjectState(Builder builder) {
    this.agentsMd = builder.agentsMd;
    this.claudeMd = builder.claudeMd;
    this.claudeDirectory = builder.claudeDirectory;
    this.installedSkills = List.copyOf(builder.installedSkills);
    this.linkedInstallRoots = Map.copyOf(builder.linkedInstallRoots);
    this.markedRuleFiles = Map.copyOf(builder.markedRuleFiles);
    this.outputDirectory = builder.outputDirectory;
    this.gradleProject = builder.gradleProject;
    assert invariant() : "a project state must describe one path once";
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The project's {@code AGENTS.md}, byte for byte, or empty when it has none. */
  public Optional<String> agentsMd() {
    return Optional.ofNullable(agentsMd);
  }

  /** The project's {@code CLAUDE.md}, byte for byte, or empty when it has none. */
  public Optional<String> claudeMd() {
    return Optional.ofNullable(claudeMd);
  }

  /** Whether the vendor directory exists — one half of the vendor-detection rule. */
  public boolean hasClaudeDirectory() {
    return claudeDirectory;
  }

  /** Every skill directory found under either install root, in read order. */
  public List<InstalledSkill> installedSkills() {
    return installedSkills;
  }

  /** The skill directory at one flavour's path, or empty when nothing is there. */
  public Optional<InstalledSkill> installedSkill(SkillFlavour flavour, String name) {
    return installedSkills.stream()
        .filter(skill -> skill.flavour() == flavour && skill.name().equals(name))
        .findFirst();
  }

  /**
   * What a flavour's install root points at when the root itself is a symbolic link — nothing may
   * be written into that flavour at all, because every page of it, present or not, would land
   * wherever the link goes.
   */
  public Optional<String> linkedInstallRoot(SkillFlavour flavour) {
    return Optional.ofNullable(linkedInstallRoots.get(flavour));
  }

  /**
   * Vendor rule files that already carry our markers, by project-relative path. The installer never
   * CREATES one of these; it keeps an existing block up to date.
   */
  public Map<String, String> markedRuleFiles() {
    return markedRuleFiles;
  }

  /** Where this project's rendered traces land, detected or {@link #DEFAULT_OUTPUT_DIRECTORY}. */
  public String outputDirectory() {
    return outputDirectory;
  }

  /** Whether this is a Gradle build — decides which commands the managed block names. */
  public boolean gradleProject() {
    return gradleProject;
  }

  /**
   * No path is described twice: one snapshot entry per (flavour, name), and nothing is listed under
   * a flavour whose whole install root is a link — what was found there was found through it.
   */
  boolean invariant() {
    long distinct =
        installedSkills.stream()
            .map(skill -> skill.flavour() + "/" + skill.name())
            .distinct()
            .count();
    return distinct == installedSkills.size()
        && installedSkills.stream()
            .noneMatch(skill -> linkedInstallRoots.containsKey(skill.flavour()))
        && outputDirectory != null
        && !outputDirectory.isBlank();
  }

  /** Assembles a snapshot; every field has the "untouched project" default. */
  public static final class Builder {

    private String agentsMd;
    private String claudeMd;
    private boolean claudeDirectory;
    private final List<InstalledSkill> installedSkills = new ArrayList<>();
    private final Map<SkillFlavour, String> linkedInstallRoots = new LinkedHashMap<>();
    private final Map<String, String> markedRuleFiles = new LinkedHashMap<>();
    private String outputDirectory = DEFAULT_OUTPUT_DIRECTORY;
    private boolean gradleProject;

    private Builder() {}

    public Builder agentsMd(String content) {
      this.agentsMd = content;
      return this;
    }

    public Builder claudeMd(String content) {
      this.claudeMd = content;
      return this;
    }

    public Builder claudeDirectory(boolean present) {
      this.claudeDirectory = present;
      return this;
    }

    public Builder addInstalledSkill(InstalledSkill skill) {
      this.installedSkills.add(skill);
      return this;
    }

    /** Records that a flavour's install root is a symbolic link, and what it points at. */
    public Builder linkedInstallRoot(SkillFlavour flavour, String target) {
      this.linkedInstallRoots.put(flavour, target);
      return this;
    }

    public Builder markedRuleFile(String relativePath, String content) {
      this.markedRuleFiles.put(relativePath, content);
      return this;
    }

    public Builder outputDirectory(String directory) {
      this.outputDirectory = directory;
      return this;
    }

    public Builder gradleProject(boolean gradle) {
      this.gradleProject = gradle;
      return this;
    }

    public ProjectState build() {
      return new ProjectState(this);
    }
  }
}
