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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import ai.narrativetrace.tooling.doctor.DoctorSnapshot;
import ai.narrativetrace.tooling.feedback.FeedbackCategory;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every flag of the feedback verb, read as a pure function over a command line. */
class FeedbackArgumentsTest {

  private static final List<String> MINIMUM =
      List.of(
          "draft",
          "--category",
          "library",
          "--step",
          "s",
          "--did",
          "d",
          "--happened",
          "h",
          "--expected",
          "e");

  private static FeedbackArguments parse(String... extra) {
    var arguments = new java.util.ArrayList<>(MINIMUM);
    arguments.addAll(List.of(extra));
    return FeedbackArguments.parse(arguments);
  }

  @Test
  void aCommandLineIsAListOfArgumentsNeverNull() {
    assertThatIllegalArgumentException().isThrownBy(() -> FeedbackArguments.parse(null));
  }

  @Test
  void readsEveryOptionalFlagInBothSpellings() {
    FeedbackArguments spaced =
        parse(
            "--language",
            "pt-BR",
            "--agent-product",
            "example-cli",
            "--agent-model",
            "example-model",
            "--trace",
            "A/a.nt",
            "--json");

    assertThat(spaced.language()).isEqualTo("pt-BR");
    assertThat(spaced.agentProduct()).isEqualTo("example-cli");
    assertThat(spaced.agentModel()).isEqualTo("example-model");
    assertThat(spaced.trace()).isEqualTo("A/a.nt");
    assertThat(spaced.json()).isTrue();

    FeedbackArguments equalsSigns =
        parse("--language=zh-CN", "--agent-model=other-model", "--trace=B/b.nt");

    assertThat(equalsSigns.language()).isEqualTo("zh-CN");
    assertThat(equalsSigns.agentModel()).isEqualTo("other-model");
    assertThat(equalsSigns.trace()).isEqualTo("B/b.nt");
  }

  @Test
  void theLanguageDefaultsToEnglishAndTheAgentToUnknown() {
    FeedbackArguments parsed = parse();

    assertThat(parsed.language()).isEqualTo("en");
    assertThat(parsed.agentProduct()).isEmpty();
    assertThat(parsed.agentModel()).isEmpty();
    assertThat(parsed.trace()).isEmpty();
    assertThat(parsed.error()).isNull();
  }

  @Test
  void helpWinsOverEveryMissingField() {
    FeedbackArguments parsed = FeedbackArguments.parse(List.of("--help"));

    assertThat(parsed.help()).isTrue();
    assertThat(parsed.error()).as("a mistyped command may still ask how it works").isNull();
  }

  @Test
  void theFirstProblemIsTheOneReported() {
    FeedbackArguments parsed = FeedbackArguments.parse(List.of("draft", "--nope", "--also-nope"));

    assertThat(parsed.error()).isEqualTo("unknown option: \"--nope\"");
  }

  @Test
  void aChannelIsTheFirstNonFlagArgumentAndOnlyTheFirst() {
    FeedbackArguments parsed = FeedbackArguments.parse(List.of("draft", "url"));

    assertThat(parsed.channel()).isEqualTo("draft");
    assertThat(parsed.error()).isEqualTo("unknown option: \"url\"");
  }

  @Test
  void aValueFlagAtTheEndOfTheLineWithNoValueIsReported() {
    assertThat(FeedbackArguments.parse(List.of("draft", "--category")).error())
        .isEqualTo("--category needs a value");
  }

  @Test
  void theReportItBuildsCarriesWhatTheProjectAndTheFlagsSayTogether() {
    var report =
        parse("--agent-product", "example-cli")
            .report(DoctorSnapshot.healthy(), "{\"findings\":[],\"exitCode\":0}");

    assertThat(report.runtime()).isEqualTo("java");
    assertThat(report.category()).isEqualTo(FeedbackCategory.LIBRARY);
    assertThat(report.install()).contains("ai.narrativetrace:narrativetrace-junit5:0.2.2");
    assertThat(report.agent().product()).isEqualTo("example-cli");
    assertThat(report.attachments().hasDoctorReport()).isTrue();
  }
}
