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

import ai.narrativetrace.tooling.frameworks.Kebab;
import java.util.List;

/**
 * One row of the platform table: marker → pointer → install path for a stranger → evidence lane.
 *
 * <p>A new platform is a new row, never new machinery: {@code init}, {@code uninstall}, the doctor,
 * the Cursor plugin manifest and the documentation page all read the same rows.
 *
 * @param id the stable row id ({@code cursor}, {@code gemini-cli}, …) — a cross-port contract
 * @param name the platform as a reader names it
 * @param markerPaths project-relative paths whose existence proves the platform is in use; any one
 *     is enough, matched as exact paths so a near-miss such as {@code .cursorx/} proves nothing
 * @param pointer what {@code init} writes for this platform
 * @param installPath how a stranger gets the skills on this platform, as the page prints it
 * @param agentFinds what the platform's agent will find in a project that ran {@code init}
 * @param evidenceLane the Tier B lane id that exercises the platform, or {@link #MANUAL}
 * @param verified the date the platform facts of this row were last checked against the vendor's
 *     own documentation, {@code yyyy-mm-dd}
 */
public record PlatformRow(
    String id,
    String name,
    List<String> markerPaths,
    Pointer pointer,
    String installPath,
    String agentFinds,
    String evidenceLane,
    String verified) {

  /** The {@link #evidenceLane()} of a platform no automated trial exercises. */
  public static final String MANUAL = "manual";

  public PlatformRow {
    if (!Kebab.isKebab(id)) {
      throw new IllegalArgumentException("a row id is kebab-case, got " + id);
    }
    requireText(name, id, "name");
    markerPaths = List.copyOf(markerPaths);
    if (markerPaths.isEmpty()) {
      throw new IllegalArgumentException("row " + id + " has no marker path");
    }
    if (pointer == null) {
      throw new IllegalArgumentException("row " + id + " leaves its pointer column empty");
    }
    requireText(installPath, id, "install path");
    requireText(agentFinds, id, "agent-finds text");
    if (!Kebab.isKebab(evidenceLane)) {
      throw new IllegalArgumentException("row " + id + " names a lane id or manual");
    }
    if (verified == null || !verified.matches("\\d{4}-\\d{2}-\\d{2}")) {
      throw new IllegalArgumentException("row " + id + " carries a yyyy-mm-dd verification date");
    }
  }

  private static void requireText(String value, String id, String column) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("row " + id + " leaves its " + column + " empty");
    }
  }
}
