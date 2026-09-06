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
package ai.narrativetrace.api.spi;

import ai.narrativetrace.api.tree.TraceTree;

/**
 * One captured trace together with the label it was captured under.
 *
 * <p>INTENT: The unit {@link ReportContributor}s consume. The name is the scenario label the
 * capturing integration assigned (a test's display name, for the JUnit extension) — output files
 * and report sections are keyed by it, so it travels with the tree rather than being re-derived.
 *
 * @param name Scenario label for this trace. Never {@code null} or blank.
 * @param tree The captured tree. Never {@code null}; may be empty.
 */
public record NamedTrace(String name, TraceTree tree) {

  /** Validates both components; a contributor must never receive a half-formed pair. */
  public NamedTrace {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be null or blank");
    }
    if (tree == null) {
      throw new IllegalArgumentException("tree must not be null");
    }
  }
}
