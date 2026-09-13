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
import java.util.Optional;

/**
 * {@code config.output-property} — {@code narrativetrace.output} (system property, environment
 * variable, or {@code gradle.properties}) set to anything other than {@code true}/{@code false}
 * (case-insensitive), the Java analogue of the TypeScript reference's {@code NARRATIVETRACE_OUTPUT}
 * check.
 */
public final class OutputPropertyCheck implements DoctorCheck {

  public static final String ID = "config.output-property";
  static final String KEY = "narrativetrace.output";

  @Override
  public Finding run(DoctorSnapshot snapshot) {
    Optional<String> value = value(snapshot);
    if (value.isEmpty()) {
      return Finding.pass(
          ID,
          "narrativetrace.output is unset — output stays on, its default",
          DocAnchors.CONFIGURATION_OUTPUT);
    }
    String v = value.get().trim();
    if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")) {
      return Finding.pass(ID, "narrativetrace.output=" + v, DocAnchors.CONFIGURATION_OUTPUT);
    }
    return Finding.fail(
        ID,
        "narrativetrace.output is set to \"" + v + "\", neither true nor false",
        "Set narrativetrace.output=true or =false (or unset it to keep the default) — any other"
            + " value is read as false today, which is silent to a reader of the config.",
        DocAnchors.CONFIGURATION_OUTPUT);
  }

  private Optional<String> value(DoctorSnapshot snapshot) {
    if (snapshot.systemProperties().containsKey(KEY)) {
      return Optional.of(snapshot.systemProperties().get(KEY));
    }
    if (snapshot.gradleProperties().containsKey(KEY)) {
      return Optional.of(snapshot.gradleProperties().get(KEY));
    }
    String envKey = "NARRATIVETRACE_OUTPUT";
    if (snapshot.env().containsKey(envKey)) {
      return Optional.of(snapshot.env().get(envKey));
    }
    return Optional.empty();
  }
}
