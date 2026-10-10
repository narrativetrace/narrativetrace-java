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
import java.util.regex.Pattern;

/**
 * What in a project's build manifests proves a framework is present — the first column of the
 * framework table.
 *
 * <p>{@code manifest} patterns are matched against the TEXT of every build file, settings file and
 * version catalog the doctor read, never against a resolved classpath: the doctor is
 * text-and-manifest only and never executes a build. {@code deferTo} names rows that take
 * precedence: a row whose marker matches stands down while any row it defers to is also present
 * (plain servlet wiring is the wrong advice for a Spring or Micronaut application, which each have
 * their own row).
 *
 * @param description the marker in a reader's words, as the docs and the doctor's message print it
 * @param manifest patterns, any one of which proves presence; empty for a row that is never
 *     detected from a manifest (an owner opt-in, or a row whose presence is an absence)
 * @param deferTo ids of rows that take precedence when their own marker matches too
 */
public record Marker(String description, List<Pattern> manifest, List<String> deferTo) {

  public Marker {
    if (description == null || description.isBlank()) {
      throw new IllegalArgumentException("a marker's description must not be blank");
    }
    manifest = List.copyOf(manifest);
    deferTo = List.copyOf(deferTo);
  }
}
