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
 * One thing an install or an uninstall would do to one path.
 *
 * <p>INTENT: the unit a plan is made of, and the reason a plan can be printed as a diff before
 * anything happens. Every action that touches a file carries the file's WHOLE text before and
 * after, so the executor needs no knowledge of the project at all and the renderer can diff without
 * reading the disk.
 *
 * <p><b>@llmNote</b> Paths are project-relative and normalized — an absolute path, or one that
 * climbs out with {@code ..}, is refused at construction. That is the only thing standing between a
 * hand-written plan and a write outside the project.
 *
 * <p><b>@pattern</b> Sealed hierarchy of records: a renderer or an executor switches exhaustively,
 * and a new kind of action is a compile error everywhere it matters rather than a silent no-op.
 */
public sealed interface Action {

  /** The project-relative path this action is about. */
  Path path();

  /** A stable token naming the kind, for text and JSON rendering. */
  String kind();

  /**
   * An action that leaves one file with a known text.
   *
   * <p><b>@llmNote</b> {@link #before()} is {@code ""} for a file that does not exist yet, {@link
   * #after()} is {@code ""} for one that is being deleted.
   */
  sealed interface FileEdit extends Action {

    /** The file's whole text before the action; {@code ""} when it does not exist. */
    String before();

    /** The file's whole text after the action; {@code ""} when it is deleted. */
    String after();
  }

  /** Writes a file that is not there yet. */
  record CreateFile(Path path, String content) implements FileEdit {

    public CreateFile {
      path = Actions.requireProjectRelative(path);
      Actions.requireText(content);
    }

    @Override
    public String before() {
      return "";
    }

    @Override
    public String after() {
      return content;
    }

    @Override
    public String kind() {
      return "create";
    }
  }

  /**
   * Rewrites a file whose NarrativeTrace-owned region changed — the managed block between the
   * markers, or, for a copied {@code SKILL.md}, the whole page.
   */
  record ReplaceBlock(Path path, String before, String after) implements FileEdit {

    public ReplaceBlock {
      path = Actions.requireProjectRelative(path);
      Actions.requireText(before);
      Actions.requireText(after);
    }

    @Override
    public String kind() {
      return "replace";
    }
  }

  /**
   * Stamps a page a registry installed, because it already IS this carrier's page in every byte but
   * the provenance line.
   *
   * <p><b>@llmNote</b> A separate kind from {@link ReplaceBlock} so that the plan, the diff and the
   * report all say "adopted" rather than "replaced": a person reading it needs to know that nothing
   * of theirs was overwritten, which is also why adoption needs no {@code --force}. {@link
   * Adoption} owns the rule about which page qualifies.
   */
  record AdoptPage(Path path, String before, String after) implements FileEdit {

    public AdoptPage {
      path = Actions.requireProjectRelative(path);
      Actions.requireText(before);
      Actions.requireText(after);
      if (before.isEmpty()) {
        throw new IllegalArgumentException("there is nothing to adopt where there is no page");
      }
    }

    @Override
    public String kind() {
      return "adopt";
    }
  }

  /** Adds the managed block to the end of an existing file, after one blank line. */
  record AppendBlock(Path path, String before, String block) implements FileEdit {

    public AppendBlock {
      path = Actions.requireProjectRelative(path);
      Actions.requireText(before);
      Actions.requireText(block);
    }

    @Override
    public String after() {
      return MarkedBlock.append(before, block);
    }

    @Override
    public String kind() {
      return "append";
    }
  }

  /** Adds one line to the end of an existing file, after one blank line. */
  record AppendLine(Path path, String before, String line) implements FileEdit {

    public AppendLine {
      path = Actions.requireProjectRelative(path);
      Actions.requireText(before);
      Actions.requireText(line);
    }

    @Override
    public String after() {
      return MarkedBlock.append(before, line + MarkedBlock.eolOf(before));
    }

    @Override
    public String kind() {
      return "append-line";
    }
  }

  /**
   * Replaces a symbolic link with a real path of the project's own, holding this flavour's page.
   *
   * <p><b>@llmNote</b> {@link #before()} is empty on purpose. The link is deleted first, so nothing
   * this PATH used to reach survives here — and what it pointed at is left exactly as it was, which
   * is the whole point: after {@code npx skills add} the vendor path links to the open-standard
   * page, and a diff showing that page's text here would read as an edit to somebody else's file.
   *
   * @param link where the symbolic link itself sits — the skill's directory, or its page
   * @param page the page this writes, and the path the plan is keyed on
   * @param target what the link pointed at, for the line a person reads
   * @param content the flavour's rendered page, already stamped
   */
  record ReplaceLink(Path link, Path page, String target, String content) implements FileEdit {

    public ReplaceLink {
      link = Actions.requireProjectRelative(link);
      page = Actions.requireProjectRelative(page);
      Actions.requireText(content);
      if (target == null || target.isBlank()) {
        throw new IllegalArgumentException("replacing a link names what it pointed at");
      }
      if (content.isEmpty()) {
        throw new IllegalArgumentException("a link is replaced by a page, never by an empty file");
      }
      if (!page.startsWith(link)) {
        throw new IllegalArgumentException(page + " is not behind the link " + link);
      }
    }

    @Override
    public Path path() {
      return page;
    }

    @Override
    public String before() {
      return "";
    }

    @Override
    public String after() {
      return content;
    }

    @Override
    public String kind() {
      return "replace-link";
    }
  }

  /** Removes a file the installer wrote. */
  record DeleteFile(Path path, String before) implements FileEdit {

    public DeleteFile {
      path = Actions.requireProjectRelative(path);
      Actions.requireText(before);
    }

    @Override
    public String after() {
      return "";
    }

    @Override
    public String kind() {
      return "delete";
    }
  }

  /**
   * Removes a directory the installer created, once its files are gone.
   *
   * <p><b>@llmNote</b> Never recursive: a directory that still holds somebody else's file is left
   * alone and reported, because deleting it would take that file with it.
   */
  record DeleteDirectory(Path path) implements Action {

    public DeleteDirectory {
      path = Actions.requireProjectRelative(path);
    }

    @Override
    public String kind() {
      return "delete-directory";
    }
  }

  /**
   * Something the installer will NOT do, and why.
   *
   * <p><b>@llmNote</b> A refusal is reported, never thrown: the rest of the plan proceeds, and the
   * run exits 1 so a script notices.
   */
  record Refuse(Path path, String reason) implements Action {

    public Refuse {
      path = Actions.requireProjectRelative(path);
      if (reason == null || reason.isBlank()) {
        throw new IllegalArgumentException("a refusal must carry a reason naming what it refused");
      }
    }

    @Override
    public String kind() {
      return "refuse";
    }
  }
}
