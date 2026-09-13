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
package ai.narrativetrace.cli.doctor.checks;

import ai.narrativetrace.cli.doctor.DocAnchors;
import ai.narrativetrace.cli.doctor.DoctorCheck;
import ai.narrativetrace.cli.doctor.DoctorSnapshot;
import ai.narrativetrace.cli.doctor.Finding;
import ai.narrativetrace.cli.doctor.Requirements;
import ai.narrativetrace.cli.doctor.Versions;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code toolchain.junit5-range} — the declared {@code org.junit.jupiter:junit-jupiter} (or {@code
 * junit-jupiter-api}) version against the range {@code narrativetrace-junit5} supports. Java has no
 * resolved-manifest peer range to read the way the TypeScript reference reads {@code vitest}'s
 * declared peer, so this reads the version out of the project's own build file text — the same
 * "declared, not resolved" limitation applies to every text-scanned check here (see the module's
 * README / the port's own report for detail).
 */
public final class Junit5RangeCheck implements DoctorCheck {

  public static final String ID = "toolchain.junit5-range";

  private static final Pattern JUPITER_COORDINATE =
      Pattern.compile("org\\.junit\\.jupiter:junit-jupiter(?:-api|-engine)?:([0-9][\\w.\\-]*)");

  @Override
  public Finding run(DoctorSnapshot snapshot) {
    Optional<String> version = declaredVersion(snapshot);
    if (version.isEmpty()) {
      return Finding.fail(
          ID,
          "No org.junit.jupiter:junit-jupiter dependency is declared",
          "Add testImplementation(\"org.junit.jupiter:junit-jupiter:5.11.4\") —"
              + " narrativetrace-junit5 is a JUnit 5 extension and needs the Jupiter engine"
              + " present.",
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
