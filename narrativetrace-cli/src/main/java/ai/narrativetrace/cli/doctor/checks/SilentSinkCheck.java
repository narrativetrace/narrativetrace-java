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
 * {@code trap.silent-sink} — tracing is wired (the extension is registered) but output is turned
 * fully off with no alternative sink in evidence (no SLF4J/Logback bridge, no custom {@code
 * EventStore}/{@code BufferedEventConsumer} usage). NarrativeTrace's Java default is output ON;
 * this trap catches the one way a project can go silent anyway — the direct analogue of the
 * TypeScript reference's "no consumer attached to a traced proxy" trap.
 */
public final class SilentSinkCheck implements DoctorCheck {

  public static final String ID = "trap.silent-sink";

  @Override
  public Finding run(DoctorSnapshot snapshot) {
    if (!snapshot.extensionRegistered()) {
      return Finding.pass(
          ID,
          "NarrativeTraceExtension is not registered — nothing traced yet",
          DocAnchors.TROUBLESHOOTING_NO_OUTPUT);
    }
    boolean outputDisabled =
        "false"
                .equalsIgnoreCase(
                    snapshot.systemProperties().getOrDefault(OutputPropertyCheck.KEY, ""))
            || "false"
                .equalsIgnoreCase(
                    snapshot.gradleProperties().getOrDefault(OutputPropertyCheck.KEY, ""));
    boolean hasAlternativeSink =
        snapshot.declaresDependency("ai.narrativetrace:narrativetrace-slf4j")
            || snapshot.declaresDependency("ai.narrativetrace:narrativetrace-opentelemetry")
            || snapshot.declaresDependency("ai.narrativetrace:narrativetrace-micrometer")
            || snapshot.sourceFiles().values().stream()
                .anyMatch(s -> s.contains("BufferedEventConsumer") || s.contains("EventStore"));
    if (!outputDisabled || hasAlternativeSink) {
      return Finding.pass(
          ID,
          "Tracing has a live sink: file output or an alternative consumer",
          DocAnchors.TROUBLESHOOTING_NO_OUTPUT);
    }
    return Finding.fail(
        ID,
        "The extension is registered, narrativetrace.output=false, and no alternative consumer"
            + " (SLF4J, OpenTelemetry, Micrometer, a custom EventStore) is in evidence — every"
            + " trace this test suite produces is being thrown away",
        "Either drop narrativetrace.output=false to keep the default file output, or attach a"
            + " consumer (narrativetrace-slf4j is the smallest: two runtimeOnly dependencies, zero"
            + " code).",
        DocAnchors.TROUBLESHOOTING_NO_OUTPUT);
  }
}
