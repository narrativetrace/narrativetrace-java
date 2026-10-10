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
 * The fourth column of the framework table: which doctor check observes the row.
 *
 * <p>Every row states one, so "unchecked" is always a decision: a row whose wiring the doctor can
 * observe from manifest and source earns its own {@link WiringCheck}; a row an existing check
 * already covers names it ({@link ExistingCheck}); a row nobody can observe from text says so
 * ({@link NoCheck}) — never a vague pass.
 */
public sealed interface CheckBinding {

  /** A {@code config.<framework>-<thing>} check the doctor runs for this row. */
  record WiringCheck(String id) implements CheckBinding {
    private static final String PREFIX = "config.";

    public WiringCheck {
      if (id == null
          || !id.startsWith(PREFIX)
          || !Kebab.isKebab(id.substring(PREFIX.length()))
          || !id.substring(PREFIX.length()).contains("-")) {
        throw new IllegalArgumentException(
            "a framework check id is config.<framework>-<thing>, got " + id);
      }
    }
  }

  /** A check the doctor already runs for its own reasons covers this row. */
  record ExistingCheck(String id) implements CheckBinding {}

  /** Nothing in a manifest or a source file can show this row's wiring. */
  record NoCheck(String reason) implements CheckBinding {}
}
