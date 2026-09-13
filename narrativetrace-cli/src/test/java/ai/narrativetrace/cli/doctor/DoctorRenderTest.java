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
package ai.narrativetrace.cli.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DoctorRenderTest {

  private static final Finding PASSING =
      Finding.pass(
          "toolchain.jdk-version", "Java 21 satisfies 17+", "https://narrativetrace.ai/docs/x");
  private static final Finding FAILING =
      Finding.fail(
          "trap.silent-sink",
          "no sink is attached",
          "attach a consumer",
          "https://narrativetrace.ai/docs/y");

  @Test
  void humanRenderPutsFailuresBeforePasses() {
    DoctorReport report = new DoctorReport(List.of(PASSING, FAILING), 1);
    String text = DoctorRender.renderHuman(report);
    assertThat(text.indexOf("[FAIL]")).isLessThan(text.indexOf("[PASS]"));
    assertThat(text)
        .contains("trap.silent-sink")
        .contains("fix:  attach a consumer")
        .contains("2 check(s), 1 finding(s)");
    assertThat(text).contains("1 finding(s). Exit code 1.");
  }

  @Test
  void humanRenderReportsACleanRun() {
    DoctorReport report = new DoctorReport(List.of(PASSING), 0);
    String text = DoctorRender.renderHuman(report);
    assertThat(text).contains("All checks passed.").doesNotContain("fix:");
  }

  @Test
  void jsonRenderIsWellFormedAndEscaped() {
    Finding withQuotesAndNewline =
        Finding.fail(
            "trap.x",
            "a \"quoted\" message\nwith a newline and a tab\tand a backslash \\",
            "fix",
            "https://narrativetrace.ai/docs/x");
    DoctorReport report = new DoctorReport(List.of(PASSING, withQuotesAndNewline), 1);
    String json = DoctorRender.renderJson(report);

    assertThat(json).contains("\"exitCode\": 1");
    assertThat(json).contains("\"id\": \"toolchain.jdk-version\"");
    assertThat(json).contains("\"status\": \"pass\"");
    assertThat(json).contains("\"status\": \"fail\"");
    assertThat(json).contains("\\\"quoted\\\"");
    assertThat(json).contains("\\n");
    assertThat(json).contains("\\t");
    assertThat(json).contains("\\\\");
  }

  @Test
  void jsonRenderEscapesControlCharacters() {
    String bell = "bell:" + Character.toString(7) + ":end";
    Finding withControlChar = Finding.pass("id", bell, "https://x");
    DoctorReport report = new DoctorReport(List.of(withControlChar), 0);
    assertThat(DoctorRender.renderJson(report)).contains("\\u0007");
  }

  @Test
  void jsonRenderHandlesASingleFindingAndAnEmptyFix() {
    DoctorReport report = new DoctorReport(List.of(PASSING), 0);
    String json = DoctorRender.renderJson(report);
    assertThat(json).contains("\"fix\": \"\"");
  }
}
