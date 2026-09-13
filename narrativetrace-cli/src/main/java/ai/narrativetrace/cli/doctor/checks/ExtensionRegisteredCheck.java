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
 * {@code config.extension-registered} — {@code NarrativeTraceExtension} registered the documented
 * way: either {@code @ExtendWith(NarrativeTraceExtension.class)} on a test class, or automatic
 * extension detection via a {@code META-INF/services} entry (JUnit Platform's {@code
 * junit.jupiter.extensions.autodetection.enabled=true}). A project that has the dependency on its
 * classpath but never registers it gets silent non-tracing — this check folds in what the
 * TypeScript reference splits into a separate "unknown option keys" check, since Java's extension
 * has no options object to mis-key: registration IS the configuration surface.
 */
public final class ExtensionRegisteredCheck implements DoctorCheck {

  public static final String ID = "config.extension-registered";

  @Override
  public Finding run(DoctorSnapshot snapshot) {
    boolean onClasspath = snapshot.declaresDependency("ai.narrativetrace:narrativetrace-junit5");
    if (!onClasspath) {
      return Finding.pass(
          ID,
          "narrativetrace-junit5 is not on the classpath — nothing to register",
          DocAnchors.CONFIGURATION_EXTENSION);
    }
    if (snapshot.extensionRegistered()) {
      String how =
          snapshot.extensionRegisteredViaServiceLoader()
              ? "automatic extension detection (META-INF/services)"
              : "@ExtendWith(NarrativeTraceExtension.class)";
      return Finding.pass(
          ID,
          "NarrativeTraceExtension is registered via " + how,
          DocAnchors.CONFIGURATION_EXTENSION);
    }
    return Finding.fail(
        ID,
        "narrativetrace-junit5 is on the classpath but NarrativeTraceExtension is never registered",
        "Add @ExtendWith(NarrativeTraceExtension.class) to your test class, or enable automatic"
            + " extension detection (junit.jupiter.extensions.autodetection.enabled=true in"
            + " junit-platform.properties).",
        DocAnchors.CONFIGURATION_EXTENSION);
  }
}
