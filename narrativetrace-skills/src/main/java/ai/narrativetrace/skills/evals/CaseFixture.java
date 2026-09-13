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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A case directory may declare which fixture to scaffold via {@code case.json}; a case without one
 * defaults to the skill's own canonical fixture, {@code sixty-seconds} — this runtime's analogue of
 * the TypeScript reference's default, {@code examples/sixty-seconds}.
 */
public final class CaseFixture {

  private static final String DEFAULT_FIXTURE = "sixty-seconds";
  private static final Pattern FIXTURE_FIELD = Pattern.compile("\"fixture\"\\s*:\\s*\"([^\"]+)\"");

  private CaseFixture() {}

  /**
   * Repo-root-relative path to the fixture {@code caseDir} names, or the default when it names
   * none.
   */
  public static String fixtureFor(Path caseDir) {
    Path manifest = caseDir.resolve("case.json");
    if (!Files.isRegularFile(manifest)) {
      return DEFAULT_FIXTURE;
    }
    String content;
    try {
      content = Files.readString(manifest);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + manifest, e);
    }
    Matcher matcher = FIXTURE_FIELD.matcher(content);
    if (!matcher.find()) {
      throw new IllegalArgumentException(manifest + " has no \"fixture\" field");
    }
    return matcher.group(1);
  }
}
