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

import java.nio.file.Path;

/**
 * One skill directory found in a consumer project, and what the installer is allowed to do with it.
 *
 * <p>INTENT: the planner's decision for a skill is a function of this record alone — is the
 * directory ours (overwrite, no flag), somebody else's (refuse unless forced), a symbolic link
 * (replace the link, or refuse — never write through it), or not a directory at all (refuse,
 * always).
 *
 * @param flavour which install root it was found under
 * @param name the directory name, which is the skill name
 * @param presence what was found there
 * @param coordinate the carrier a previous install stamped it with, empty unless {@link
 *     Presence#OURS}
 * @param body the current {@code SKILL.md} text; for a link, the page it reaches INSIDE the
 *     project, and empty when it reaches none
 * @param link what the symbolic link points at, exactly as the filesystem reports it — empty unless
 *     the presence is one of the two linked ones
 */
public record InstalledSkill(
    SkillFlavour flavour,
    String name,
    Presence presence,
    String coordinate,
    String body,
    String link) {

  /** What sits at a skill's path in the consumer project. */
  public enum Presence {
    /** A directory whose {@code SKILL.md} carries our provenance line — an install of ours. */
    OURS,
    /** A directory somebody else owns: no page, or a page without our provenance line. */
    FOREIGN,
    /** Something that is not a directory at all sits at the path a skill needs. */
    NOT_A_DIRECTORY,
    /** The skill's directory is a symbolic link; what a registry leaves at the vendor path. */
    LINKED_DIRECTORY,
    /** The directory is real, and its {@code SKILL.md} is a symbolic link. */
    LINKED_PAGE
  }

  /**
   * A skill nothing links to — the shape every install of ours, every foreign directory and every
   * file-in-the-way has, and the reason adding the link did not touch thirty-seven call sites.
   */
  public InstalledSkill(
      SkillFlavour flavour, String name, Presence presence, String coordinate, String body) {
    this(flavour, name, presence, coordinate, body, "");
  }

  /** The page's file name inside a skill directory — the same in every flavour. */
  public static final String PAGE = "SKILL.md";

  public InstalledSkill {
    if (flavour == null || presence == null) {
      throw new IllegalArgumentException("an installed skill needs a flavour and a presence");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("an installed skill's name must not be blank");
    }
    if (coordinate == null || body == null) {
      throw new IllegalArgumentException("use \"\" for an unknown coordinate or body, never null");
    }
    if (presence == Presence.OURS && coordinate.isBlank()) {
      throw new IllegalArgumentException("an installed skill of ours carries its coordinate");
    }
    if (link == null) {
      throw new IllegalArgumentException("use \"\" for a path nothing links to, never null");
    }
    if (isLinked(presence) == link.isBlank()) {
      throw new IllegalArgumentException(
          "a linked presence names what the link points at, and only a linked one does");
    }
  }

  /** Whether this presence is one of the two the installer must not write through. */
  private static boolean isLinked(Presence presence) {
    return presence == Presence.LINKED_DIRECTORY || presence == Presence.LINKED_PAGE;
  }

  /** The project-relative directory, e.g. {@code .agents/skills/narrativetrace-doctor}. */
  public Path directory() {
    return Path.of(flavour.installRoot(), name);
  }

  /** The project-relative page, e.g. {@code .agents/skills/narrativetrace-doctor/SKILL.md}. */
  public Path page() {
    return directory().resolve(PAGE);
  }

  /**
   * Where the symbolic link itself sits: the skill's directory, or its page.
   *
   * @throws IllegalStateException when nothing here is a link, which is a caller reading a field
   *     that has no meaning rather than a project in a strange state
   */
  public Path linkedAt() {
    if (!isLinked(presence)) {
      throw new IllegalStateException("nothing links to " + directory());
    }
    return presence == Presence.LINKED_PAGE ? page() : directory();
  }
}
