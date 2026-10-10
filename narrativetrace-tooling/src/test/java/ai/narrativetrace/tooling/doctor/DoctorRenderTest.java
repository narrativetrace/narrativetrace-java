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
package ai.narrativetrace.tooling.doctor;

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

  /**
   * A framework fix carries its wiring snippet, several lines long. Every continuation line sits
   * under the fix, indented past the label, so the snippet never reads as the next finding's text;
   * the JSON keeps the lines exactly as written.
   */
  @Test
  void humanRenderIndentsAMultiLineFixUnderItsLabel() {
    Finding multiLine =
        Finding.fail(
            "config.spring-enabled",
            "never applied",
            "Apply the wiring:\n@Configuration\npublic class C {}\n",
            "https://narrativetrace.ai/docs/z");
    String text = DoctorRender.renderHuman(new DoctorReport(List.of(multiLine), 1));
    assertThat(text)
        .contains(
            "  fix:  Apply the wiring:\n"
                + "        @Configuration\n"
                + "        public class C {}\n"
                + "  skill:");
    assertThat(DoctorRender.renderJson(new DoctorReport(List.of(multiLine), 1)))
        .contains("Apply the wiring:\\n@Configuration\\npublic class C {}\\n");
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

  /**
   * The skill sits under the fix, where a reader is already looking for what to do next — and only
   * on a failure, because a passing check needs nobody to follow anything.
   */
  @Test
  void humanRenderNamesTheFixingSkillUnderTheFix() {
    DoctorReport report = new DoctorReport(List.of(PASSING, FAILING), 1);
    String text = DoctorRender.renderHuman(report);
    assertThat(text).contains("fix:  attach a consumer\n  skill: narrativetrace-doctor\n");
  }

  @Test
  void humanRenderSaysNothingAboutASkillWhereNoneFixesTheFinding() {
    Finding noSkill =
        Finding.fail(
            "toolchain.jdk-version",
            "Java 11 does not satisfy 17+",
            "Upgrade the JDK",
            "https://narrativetrace.ai/docs/x");
    String text = DoctorRender.renderHuman(new DoctorReport(List.of(noSkill), 1));
    assertThat(text).contains("fix:  Upgrade the JDK").doesNotContain("skill:");
  }

  @Test
  void humanRenderSaysNothingAboutASkillOnAPassingCheck() {
    Finding passingWithASkill =
        Finding.pass("trap.silent-sink", "a sink is attached", "https://narrativetrace.ai/docs/y");
    assertThat(passingWithASkill.skill()).isEqualTo("narrativetrace-doctor");
    assertThat(DoctorRender.renderHuman(new DoctorReport(List.of(passingWithASkill), 0)))
        .doesNotContain("skill:");
  }

  @Test
  void jsonRenderEmitsTheSkillAsAStringOrAsNull() {
    DoctorReport report = new DoctorReport(List.of(PASSING, FAILING), 1);
    String json = DoctorRender.renderJson(report);
    assertThat(json).contains("\"skill\": null");
    assertThat(json).contains("\"skill\": \"narrativetrace-doctor\"");
  }

  /**
   * The separator between findings. A comma after the LAST object makes the array invalid, and the
   * `skill` key is now what the comma sits behind — so the tail of the array is worth asserting
   * literally rather than through a "looks like JSON" proxy.
   */
  @Test
  void jsonRenderSeparatesFindingsAndNeverTrailsAComma() {
    String json = DoctorRender.renderJson(new DoctorReport(List.of(PASSING, FAILING), 1));

    assertThat(json).contains("    },\n    {").doesNotContain("},\n  ]");
    assertThat(json).contains("\n  ],\n  \"exitCode\": 1\n}\n");
  }

  /**
   * Each finding is printed exactly once: the failures pass and the passes pass must not overlap.
   */
  @Test
  void humanRenderPrintsEachFindingOnceAndOnlyOnce() {
    String text = DoctorRender.renderHuman(new DoctorReport(List.of(PASSING, FAILING), 1));

    assertThat(text.split("\\[FAIL\\]", -1)).hasSize(2);
    assertThat(text.split("\\[PASS\\]", -1)).hasSize(2);
  }

  /** A space is not a control character: escaping one would make every message unreadable. */
  @Test
  void jsonRenderLeavesOrdinaryWhitespaceAlone() {
    Finding withSpaces = Finding.pass("id", "two words here", "https://x");

    assertThat(DoctorRender.renderJson(new DoctorReport(List.of(withSpaces), 0)))
        .contains("\"message\": \"two words here\"")
        .doesNotContain("\\u0020");
  }
}
