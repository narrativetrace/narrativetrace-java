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
package ai.narrativetrace.skills.evals;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the repository root independent of the invoking shell's working directory.
 *
 * <p>INTENT: {@link EvalRunner} used to trust {@code Path.of("").toAbsolutePath()} (the caller's
 * cwd) as the repo root outright — correct only when launched from the repo root itself, and
 * silently wrong (a "No such file" resolving {@code evals/<skill>/<case>}) when launched from
 * {@code narrativetrace-skills/} the way the module's own README shows for a direct {@code java
 * -jar} invocation. This resolves the same way for the Gradle task, a classpath launch and a jar
 * launch, from either directory.
 *
 * @llmNote resolution order: (1) the cwd already IS the repo root ({@code settings.gradle.kts}
 *     present); (2) the cwd IS the module directory (its name is {@code narrativetrace-skills} and
 *     its parent carries the root marker); (3) fall back to walking up from wherever this class's
 *     own {@code .class}/jar lives on disk — this works from any other launch directory, because
 *     the built artifact always lives under {@code narrativetrace-skills/build/...} regardless of
 *     the process's cwd.
 */
public final class RepoRoot {

  private static final String MODULE_DIR_NAME = "narrativetrace-skills";
  private static final String ROOT_MARKER = "settings.gradle.kts";

  private RepoRoot() {}

  /** The production entry point: resolves from the real process cwd. */
  public static Path locate() {
    return locate(Path.of("").toAbsolutePath());
  }

  /** Cwd-injectable for tests; production code always calls {@link #locate()}. */
  static Path locate(Path cwd) {
    if (Files.exists(cwd.resolve(ROOT_MARKER))) {
      return cwd;
    }
    if (isNamed(cwd, MODULE_DIR_NAME) && hasRootMarker(cwd.getParent())) {
      return cwd.getParent();
    }
    return locateFromCodeSource();
  }

  private static Path locateFromCodeSource() {
    Path location = codeSourceLocation();
    for (Path dir = location; dir != null; dir = dir.getParent()) {
      if (isNamed(dir, MODULE_DIR_NAME) && hasRootMarker(dir.getParent())) {
        return dir.getParent();
      }
    }
    throw new IllegalStateException("Could not locate the repo root from cwd or from " + location);
  }

  private static boolean isNamed(Path dir, String name) {
    Path fileName = dir.getFileName();
    return fileName != null && name.equals(fileName.toString());
  }

  private static boolean hasRootMarker(Path dir) {
    return dir != null && Files.exists(dir.resolve(ROOT_MARKER));
  }

  private static Path codeSourceLocation() {
    var url = RepoRoot.class.getProtectionDomain().getCodeSource().getLocation();
    try {
      return Path.of(url.toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Could not resolve code source location: " + url, e);
    }
  }
}
