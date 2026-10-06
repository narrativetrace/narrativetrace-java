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
package ai.narrativetrace.tooling.doctor.checks;

import ai.narrativetrace.tooling.doctor.DocAnchors;
import ai.narrativetrace.tooling.doctor.DoctorCheck;
import ai.narrativetrace.tooling.doctor.DoctorSnapshot;
import ai.narrativetrace.tooling.doctor.Finding;
import ai.narrativetrace.tooling.doctor.Requirements;
import ai.narrativetrace.tooling.doctor.Versions;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code toolchain.junit5-range} — the declared {@code org.junit.jupiter:junit-jupiter*} version
 * against the range {@code narrativetrace-junit5} supports. Java has no resolved-manifest peer
 * range to read the way the TypeScript reference reads {@code vitest}'s declared peer, so this
 * reads the version out of the project's own build file text — the same "declared, not resolved"
 * limitation applies to every text-scanned check here (see the module's README / the port's own
 * report for detail).
 *
 * <p>INTENT: catch a JUnit that NarrativeTrace's JUnit 5 integration cannot run with. A project
 * that declares no JUnit at all — the console app {@code documentation/llms.txt}'s install block
 * builds is one, and so is every library a reader points the doctor at first — has nothing for that
 * rule to hold, so it PASSES. This check never asks a project to adopt JUnit; a JUnit-5-using
 * project's wiring is {@code config.extension-registered}'s subject, not this one's.
 *
 * @llmNote the artifact half of the coordinate is matched as a family, not a fixed list: {@code
 *     junit-jupiter}, {@code -api}, {@code -engine}, {@code -params} and any sibling all declare a
 *     Jupiter version, so the range applies to every one of them. A Jupiter dependency whose
 *     version comes from a BOM rather than a literal is invisible here exactly as it is to {@link
 *     LauncherCheck} — the whole doctor reads declared text and never resolves.
 */
public final class Junit5RangeCheck implements DoctorCheck {

  public static final String ID = "toolchain.junit5-range";

  private static final Pattern JUPITER_COORDINATE =
      Pattern.compile("org\\.junit\\.jupiter:junit-jupiter[\\w.\\-]*:([0-9][\\w.\\-]*)");

  @Override
  public Finding run(DoctorSnapshot snapshot) {
    Optional<String> version = declaredVersion(snapshot);
    if (version.isEmpty()) {
      return Finding.pass(
          ID,
          "no JUnit 5 dependency declared; the range rule applies only when one is",
          DocAnchors.INSTALLATION_DEPENDENCIES);
    }
    String v = version.get();
    if (Versions.inRange(v, Requirements.JUNIT5_MIN, Requirements.JUNIT5_MAX_EXCLUSIVE)) {
      return Finding.pass(
          ID,
          "JUnit Jupiter "
              + v
              + " is within the supported range ["
              + Requirements.JUNIT5_MIN
              + ", "
              + Requirements.JUNIT5_MAX_EXCLUSIVE
              + ")",
          DocAnchors.INSTALLATION_DEPENDENCIES);
    }
    return Finding.fail(
        ID,
        "JUnit Jupiter "
            + v
            + " is outside the supported range ["
            + Requirements.JUNIT5_MIN
            + ", "
            + Requirements.JUNIT5_MAX_EXCLUSIVE
            + ")",
        "Install a junit-jupiter version satisfying ["
            + Requirements.JUNIT5_MIN
            + ", "
            + Requirements.JUNIT5_MAX_EXCLUSIVE
            + ") — narrativetrace-junit5 targets that range.",
        DocAnchors.INSTALLATION_DEPENDENCIES);
  }

  private Optional<String> declaredVersion(DoctorSnapshot snapshot) {
    Matcher m = JUPITER_COORDINATE.matcher(snapshot.buildFileContent());
    return m.find() ? Optional.of(m.group(1)) : Optional.empty();
  }
}
