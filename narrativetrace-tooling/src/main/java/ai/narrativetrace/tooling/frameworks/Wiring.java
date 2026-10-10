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

import java.util.List;
import java.util.stream.Stream;

/**
 * The third column of the framework table: what a project does, beyond referencing the module, for
 * the integration to take effect — and how the doctor can tell it did.
 */
public sealed interface Wiring {

  /** The wiring in a reader's words. */
  String description();

  /**
   * Wiring a project writes into its own source or configuration. The lines are NEVER typed here:
   * {@code fixture} (repository-relative) and {@code region} name a compiled, tested fixture, and
   * the text a reader sees is that fixture's, carried by {@link WiringSnippets}.
   *
   * @param fixture the repository-relative path of the compiled fixture holding the lines
   * @param region the {@code snippet:begin} region inside it, or {@code null} for the whole file
   * @param language the fence language the lines render with
   * @param evidence what in the project's sources and resources proves the wiring was applied — any
   *     one item suffices
   */
  record Snippet(
      String description, String fixture, String region, String language, List<Evidence> evidence)
      implements Wiring {
    public Snippet {
      if (fixture == null || fixture.isBlank()) {
        throw new IllegalArgumentException("a snippet names the fixture it comes from");
      }
      evidence = List.copyOf(evidence);
      if (evidence.isEmpty()) {
        throw new IllegalArgumentException("source wiring needs evidence the doctor can look for");
      }
    }

    /** Whether any of the project's source or configuration texts shows the wiring applied. */
    public boolean appliedIn(Stream<String> texts) {
      return texts.anyMatch(text -> evidence.stream().anyMatch(e -> e.foundIn(text)));
    }
  }

  /** The module's dependency lines are the whole wiring: it attaches itself once present. */
  record DependenciesOnly(String description) implements Wiring {}

  /** Wiring nobody applies on a project's behalf: the owner decides, outside the build. */
  record OwnerOptIn(String description) implements Wiring {}
}
