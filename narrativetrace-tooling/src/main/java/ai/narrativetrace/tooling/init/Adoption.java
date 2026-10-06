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

/**
 * Whether a page nobody stamped is nevertheless ours.
 *
 * <p>INTENT: a project can get these skills from a registry before it ever runs {@code init} — a
 * registry installs THIS repository's own rendered pages, out of the public git tree, without a
 * provenance line. Such a page is not a foreign page at all; it is ours, unstamped. Recognising
 * that is what lets {@code init} adopt it instead of refusing it, and it is the one place the
 * distinction is decided.
 *
 * <p><b>@llmNote</b> The comparison is the rendered bytes with ONLY the line ending normalised. A
 * trailing space, a reordered frontmatter key, a missing final newline, another release's wording,
 * or the other flavour's page is NOT adoptable — it is either somebody's edit or another release,
 * and both of those are exactly what the refusal exists to protect.
 *
 * <p><b>@sideEffects</b> None. A pure comparison of two strings.
 */
final class Adoption {

  private Adoption() {}

  /**
   * Whether {@code installed} is the carrier's own {@code rendered} page for some flavour.
   *
   * <p>An empty installed page is never adoptable: a skill directory with no page at all is a
   * directory somebody else made, not a copy of ours.
   */
  static boolean isAdoptable(String installed, String rendered) {
    if (installed == null || rendered == null) {
      throw new IllegalArgumentException("adoption compares two pages, never null");
    }
    return !installed.isEmpty() && withOneLineEnding(installed).equals(withOneLineEnding(rendered));
  }

  /** Both spellings of a line ending read as one, and nothing else about the page is touched. */
  private static String withOneLineEnding(String page) {
    return page.replace("\r\n", "\n").replace('\r', '\n');
  }
}
