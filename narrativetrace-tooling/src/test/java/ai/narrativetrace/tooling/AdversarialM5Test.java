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
package ai.narrativetrace.tooling;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.tooling.doctor.DoctorSnapshot;
import ai.narrativetrace.tooling.doctor.SnapshotBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The adversarial pass over {@code tooling.doctor} and {@code tooling.init} after milestone 5:
 * behaviour the production code has and no other test exercises. Deliberately in the PARENT
 * package, so only the public surface is reachable — a case that compiles here is a case an entry
 * point could really write.
 *
 * <p>Every case here survived being read: of sixteen proposed, most restated an assertion another
 * test already makes, and one claimed a defect the code does not have. What is left is the
 * combinations and the near misses around this milestone's own change: the doctor no longer reading
 * its own report. (A fourth survivor — a refresh combination — lives in {@code RefreshPlannerTest},
 * because stamping a page needs package-private {@code Provenance}, which is exactly the boundary
 * this package exists to respect.)
 */
class AdversarialM5Test {

  /** The exclusion is by NAME, so it holds however deep under the output root the report sits. */
  @Test
  void skipsTheDoctorsOwnReportAtAnyDepthUnderTheOutputRoot(@TempDir Path project)
      throws IOException {
    Path nested = project.resolve("build/narrativetrace/reports");
    Files.createDirectories(nested);
    Files.writeString(nested.resolve("doctor-report.json"), "arg0 arg1");
    Files.writeString(project.resolve("build/narrativetrace/trace.nt"), "placeOrder(customerId)");

    DoctorSnapshot snapshot = SnapshotBuilder.build(project);

    assertThat(snapshot.outputFiles()).containsOnlyKeys("build/narrativetrace/trace.nt");
  }

  /**
   * And it is EXACTLY the name we write, case included. A project's own {@code Doctor-Report.json}
   * is somebody else's file and stays in scope: the rule is "skip the artifact this tool produces",
   * not "skip anything whose name looks like it".
   */
  @Test
  void skipsOnlyTheExactNameTheDoctorWrites(@TempDir Path project) throws IOException {
    Path output = project.resolve("build/narrativetrace");
    Files.createDirectories(output);
    Files.writeString(output.resolve("Doctor-Report.json"), "arg0 in somebody else's file");
    Files.writeString(output.resolve("my-doctor-report.json"), "arg0 in a differently named file");

    DoctorSnapshot snapshot = SnapshotBuilder.build(project);

    assertThat(snapshot.outputFiles())
        .containsOnlyKeys(
            "build/narrativetrace/Doctor-Report.json",
            "build/narrativetrace/my-doctor-report.json");
  }

  /** Outside both output roots the name means nothing: the file is not classified at all. */
  @Test
  void aDoctorReportOutsideTheOutputRootsIsNeitherOutputNorSource(@TempDir Path project)
      throws IOException {
    Files.writeString(project.resolve("doctor-report.json"), "arg0 arg1");
    Path config = project.resolve("config");
    Files.createDirectories(config);
    Files.writeString(config.resolve("doctor-report.json"), "arg0 arg1");

    DoctorSnapshot snapshot = SnapshotBuilder.build(project);

    assertThat(snapshot.outputFiles()).isEmpty();
    assertThat(snapshot.sourceFiles()).isEmpty();
  }
}
