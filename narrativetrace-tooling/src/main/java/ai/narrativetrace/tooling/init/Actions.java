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
 * The guards every {@link Action} shares.
 *
 * <p>INTENT: a plan is data, and data can come from anywhere — so the rule that an action never
 * names a path outside the project is enforced where the action is built, not where it is applied.
 */
final class Actions {

  private Actions() {}

  /**
   * The same path, normalized, or a refusal.
   *
   * @throws IllegalArgumentException when the path is absent, absolute, empty, or climbs out of the
   *     project with {@code ..}
   */
  static Path requireProjectRelative(Path path) {
    if (path == null) {
      throw new IllegalArgumentException("an action needs a path");
    }
    Path normalized = path.normalize();
    if (path.isAbsolute() || normalized.toString().isEmpty() || normalized.startsWith("..")) {
      throw new IllegalArgumentException(
          "an action's path must be project-relative and stay inside it, got \"" + path + "\"");
    }
    return normalized;
  }

  /** Text an action carries is never null; absence is the empty string. */
  static void requireText(String text) {
    if (text == null) {
      throw new IllegalArgumentException("an action's text is \"\" when absent, never null");
    }
  }
}
