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
package ai.narrativetrace.tooling.platforms;

/**
 * What {@code init} writes so a platform's agent finds the skills before it happens to look — the
 * pointer column of the platform table.
 *
 * <p>INTENT: a pointer is never a copy of the skill pages (no third page flavour) and never a
 * symbolic link; it is one small file the platform already reads first. A platform that reads
 * {@code .agents/skills} and {@code AGENTS.md} on its own has {@link None}.
 *
 * <p><b>@pattern</b> Sealed hierarchy of records: the planner switches exhaustively, so a new kind
 * of pointer is a compile error everywhere it matters.
 */
public sealed interface Pointer {

  /** The pointer file, project-relative with {@code /} separators; empty for {@link None}. */
  String path();

  /** The platform needs no pointer: it reads {@code .agents/skills} and {@code AGENTS.md}. */
  record None() implements Pointer {
    @Override
    public String path() {
      return "";
    }
  }

  /**
   * The same managed section {@code AGENTS.md} carries, in a platform's own always-on memory file
   * (Replit's {@code replit.md}) — same markers, same coordinate stamp.
   *
   * @param path the memory file, project-relative
   */
  record ManagedSection(String path) implements Pointer {
    public ManagedSection {
      requirePath(path);
    }
  }

  /**
   * One rule file of the platform's own format (a Cursor {@code .mdc} rule) carrying the pointer
   * sentence inside a managed section.
   *
   * @param path the rule file, project-relative
   * @param text the byte-stable pointer sentence the rule carries
   */
  record Rule(String path, String text) implements Pointer {
    public Rule {
      requirePath(path);
      if (text == null || text.isBlank()) {
        throw new IllegalArgumentException("a rule pointer carries text");
      }
    }
  }

  private static void requirePath(String path) {
    if (path == null || path.isBlank() || path.startsWith("/") || path.contains("..")) {
      throw new IllegalArgumentException(
          "a pointer path is project-relative and stays inside it, got " + path);
    }
  }
}
