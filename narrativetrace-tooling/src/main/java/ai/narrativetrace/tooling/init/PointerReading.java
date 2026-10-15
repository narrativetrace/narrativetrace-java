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

import ai.narrativetrace.tooling.platforms.PlatformRow;
import ai.narrativetrace.tooling.platforms.PlatformTable;
import java.util.ArrayList;
import java.util.List;

/**
 * What one detected platform's pointer file looks like in a project — the installer's own reading,
 * handed to the doctor so both agree on what "present" means.
 *
 * <p>INTENT: the doctor must not scan marker text itself ({@code MarkedBlock} is the one place that
 * decides what counts as a marker), so the installer's package turns a {@link ProjectState} into
 * these four-field facts and the doctor only judges them.
 *
 * @param platformId the platform-table row id
 * @param platformName the platform as a reader names it
 * @param path the pointer file, project-relative
 * @param status what is at that path
 * @param coordinate the carrier coordinate on the section's opening marker; {@code ""} when the
 *     status is not {@link Status#SECTION} or the section carries no stamp
 */
public record PointerReading(
    String platformId, String platformName, String path, Status status, String coordinate) {

  /** What is at a pointer's path. */
  public enum Status {
    /** Nothing is there. */
    ABSENT,
    /** The path, or a directory above it, is a symbolic link; nothing is read or written. */
    LINKED,
    /** A file is there, but it does not carry exactly one NarrativeTrace section. */
    FOREIGN,
    /** A file with exactly one NarrativeTrace section, stamped or not. */
    SECTION
  }

  /**
   * One reading per DETECTED platform whose row has a pointer, in platform-table order. A platform
   * the project is not on has nothing to be missing, and a row with no pointer has nothing to read.
   */
  public static List<PointerReading> readAll(ProjectState state) {
    if (state == null) {
      throw new IllegalArgumentException("reading pointers needs a project state");
    }
    List<PointerReading> readings = new ArrayList<>();
    for (PlatformRow row : PlatformTable.ROWS) {
      String path = row.pointer().path();
      if (!path.isEmpty() && state.detectedPlatforms().contains(row.id())) {
        readings.add(read(row, path, state));
      }
    }
    return List.copyOf(readings);
  }

  private static PointerReading read(PlatformRow row, String path, ProjectState state) {
    if (state.pointerBehindLink(path)) {
      return new PointerReading(row.id(), row.name(), path, Status.LINKED, "");
    }
    if (state.pointerFile(path).isEmpty()) {
      return new PointerReading(row.id(), row.name(), path, Status.ABSENT, "");
    }
    MarkedBlock.Scan scan = MarkedBlock.scan(state.pointerFile(path).get());
    if (!scan.hasExactlyOneRegion()) {
      return new PointerReading(row.id(), row.name(), path, Status.FOREIGN, "");
    }
    return new PointerReading(
        row.id(), row.name(), path, Status.SECTION, scan.regions().get(0).coordinate());
  }
}
