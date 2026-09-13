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

/**
 * {@code trap.redaction-proof} — no test file asserts the literal {@code "[REDACTED]"}. Named
 * {@code trap.redaction-proof} deliberately: the {@code narrativetrace-doctor} skill's own {@code
 * verify} for the "prove redaction in a test" step asserts this finding, not a raw count of matches
 * (frozen ruling, {@code agent-skills-2026-09-12.md} §7 ruling 9).
 */
public final class RedactionProofCheck implements DoctorCheck {

  public static final String ID = "trap.redaction-proof";

  private static final String REDACTED_LITERAL = "\"[REDACTED]\"";

  @Override
  public Finding run(DoctorSnapshot snapshot) {
    boolean tracingInUse =
        snapshot.declaresDependency("ai.narrativetrace:narrativetrace-junit5")
            || snapshot.declaresDependency("ai.narrativetrace:narrativetrace-proxy")
            || snapshot.declaresDependency("ai.narrativetrace:narrativetrace-agent");
    if (!tracingInUse) {
      return Finding.pass(
          ID,
          "NarrativeTrace is not in use — nothing to prove redacted",
          DocAnchors.PRIVACY_REDACTION);
    }
    boolean proven =
        snapshot.sourceFiles().entrySet().stream()
            .filter(e -> isTestFile(e.getKey()))
            .anyMatch(e -> e.getValue().contains(REDACTED_LITERAL));
    if (proven) {
      return Finding.pass(
          ID, "A test asserts the literal \"[REDACTED]\" is present", DocAnchors.PRIVACY_REDACTION);
    }
    return Finding.fail(
        ID,
        "No test file asserts the literal \"[REDACTED]\"",
        "Render a call with a deny-listed parameter name (password, token, secret, ...), assert"
            + " the rendered text contains \"[REDACTED]\", and assert a neighboring,"
            + " non-sensitive value is still present.",
        DocAnchors.PRIVACY_REDACTION);
  }

  private static boolean isTestFile(String path) {
    return path.contains("/test/") || path.endsWith("Test.java") || path.endsWith("Tests.java");
  }
}
