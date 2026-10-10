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

import java.util.regex.Pattern;

/**
 * The three shapes a framework marker or a module reference takes in a build manifest, as patterns
 * over its text. Each one is anchored on quotes, so a longer artifact name ({@code
 * junit:junit-dep}) or a bare mention is never mistaken for the one asked about; comment-only lines
 * never reach them ({@code DoctorSnapshot#manifestText()} drops those). A quoted coordinate in some
 * other string — a description, say — still matches: telling those apart needs a Gradle parser, not
 * a pattern.
 *
 * <p><b>@llmNote</b> Parts are joined with {@code String.join}, not {@code +}, on purpose: string
 * concatenation compiles to {@code invokedynamic}, which JDepend 2.9.1 cannot parse, and this is
 * one of the two concrete classes ({@link Kebab} is the other) that keep the package's measured
 * abstractness honest for the JDepend gates.
 */
final class ManifestPatterns {

  private static final String QUOTE = "[\"']";
  private static final String COORDINATE_END = "(?::|[\"'])";

  private ManifestPatterns() {}

  /** {@code "group:artifact"} or {@code "group:artifact:version"}, exactly that artifact. */
  static Pattern dependency(String groupAndArtifact) {
    return Pattern.compile(String.join("", QUOTE, Pattern.quote(groupAndArtifact), COORDINATE_END));
  }

  /** Any artifact of the group whose name starts with {@code artifactPrefix}. */
  static Pattern dependencyPrefix(String groupAndArtifactPrefix) {
    return Pattern.compile(
        String.join(
            "", QUOTE, Pattern.quote(groupAndArtifactPrefix), "[\\w.\\-]*", COORDINATE_END));
  }

  /** A Gradle plugin id, quoted, as {@code id("…")}, {@code id '…'} or a catalog entry has it. */
  static Pattern plugin(String pluginId) {
    return Pattern.compile(String.join("", QUOTE, Pattern.quote(pluginId), QUOTE));
  }
}
