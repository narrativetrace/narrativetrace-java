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
package ai.narrativetrace.cli;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.cli.doctor.DoctorSnapshot;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CliTest {

  private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
  private final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
  private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

  private Cli.Deps deps(java.util.function.Function<Path, DoctorSnapshot> snapshotFn) {
    return new Cli.Deps(Path.of("."), snapshotFn, out, err);
  }

  private String stdout() {
    return outBytes.toString(StandardCharsets.UTF_8);
  }

  private String stderr() {
    return errBytes.toString(StandardCharsets.UTF_8);
  }

  @Test
  void noArgsPrintsUsageAndExits2() {
    int code = Cli.run(new String[0], deps(p -> DoctorSnapshot.healthy()));
    assertThat(code).isEqualTo(2);
    assertThat(stderr()).contains("narrativetrace doctor [--json]");
  }

  @Test
  void topLevelHelpExits0() {
    assertThat(Cli.run(new String[] {"--help"}, deps(p -> DoctorSnapshot.healthy()))).isZero();
    assertThat(stdout()).contains("Usage:");
    outBytes.reset();
    assertThat(Cli.run(new String[] {"-h"}, deps(p -> DoctorSnapshot.healthy()))).isZero();
    assertThat(stdout()).contains("Usage:");
  }

  @Test
  void unknownCommandExits2() {
    int code = Cli.run(new String[] {"frobnicate"}, deps(p -> DoctorSnapshot.healthy()));
    assertThat(code).isEqualTo(2);
    assertThat(stderr()).contains("Unknown command: frobnicate");
  }

  @Test
  void doctorHelpExits0() {
    assertThat(Cli.run(new String[] {"doctor", "--help"}, deps(p -> DoctorSnapshot.healthy())))
        .isZero();
    assertThat(stdout()).contains("Exit 0 = clean, 1 = findings, 2 = could not run.");
    outBytes.reset();
    assertThat(Cli.run(new String[] {"doctor", "-h"}, deps(p -> DoctorSnapshot.healthy())))
        .isZero();
    assertThat(stdout()).contains("Exit 0 = clean");
  }

  @Test
  void doctorUnknownOptionExits2() {
    int code = Cli.run(new String[] {"doctor", "--nope"}, deps(p -> DoctorSnapshot.healthy()));
    assertThat(code).isEqualTo(2);
    assertThat(stderr()).contains("Unknown doctor option: --nope");
  }

  @Test
  void doctorOnAHealthySnapshotExits0AndPrintsHumanText() {
    int code = Cli.run(new String[] {"doctor"}, deps(p -> DoctorSnapshot.healthy()));
    assertThat(code).isZero();
    assertThat(stdout()).contains("All checks passed.");
  }

  @Test
  void doctorJsonOnADirtySnapshotExits1AndPrintsJson() {
    int code =
        Cli.run(new String[] {"doctor", "--json"}, deps(p -> DoctorSnapshot.builder().build()));
    assertThat(code).isEqualTo(1);
    assertThat(stdout()).contains("\"findings\"").contains("\"exitCode\": 1");
  }

  @Test
  void standardDepsUsesTheRealFilesystemAndStreams() {
    Cli.Deps standard = Cli.Deps.standard();
    assertThat(standard.cwd()).isAbsolute();
    assertThat(standard.out()).isSameAs(System.out);
    assertThat(standard.err()).isSameAs(System.err);
  }
}
