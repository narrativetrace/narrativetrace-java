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
package ai.narrativetrace.tooling.frameworks;

/**
 * The id shape the framework table's row and check ids share: lowercase ASCII letters and digits,
 * in one or more non-empty runs joined by single hyphens. Checked by hand, not by a pattern, so no
 * id check is a nested quantifier.
 *
 * <p><b>@llmNote</b> Deliberately free of lambdas, method references and string concatenation:
 * JDepend 2.9.1 skips every class that carries an {@code invokedynamic}, and this is the one
 * concrete class it can see in a package of records and sealed interfaces — without it the
 * cross-module gate reads the package as purely abstract (distance 1.0).
 */
final class Kebab {

  private Kebab() {}

  static boolean isKebab(String id) {
    if (id == null) {
      return false;
    }
    boolean runOpen = false;
    for (int i = 0; i < id.length(); i++) {
      char c = id.charAt(i);
      if (c == '-' && runOpen) {
        runOpen = false;
      } else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
        runOpen = true;
      } else {
        return false;
      }
    }
    return runOpen;
  }
}
