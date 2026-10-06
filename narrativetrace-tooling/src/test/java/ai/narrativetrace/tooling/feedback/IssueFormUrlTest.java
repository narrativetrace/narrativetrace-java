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
package ai.narrativetrace.tooling.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/** The pre-filled issue-form URL: which fields it carries, which it must not, and how long. */
class IssueFormUrlTest {

  @Test
  void namesTheFormAndPreFillsEveryShortField() {
    String url = IssueFormUrl.of(Reports.complete());

    assertThat(url)
        .startsWith("https://github.com/narrativetrace/narrativetrace-java/issues/new?")
        .contains("template=narrativetrace-report.yml")
        .contains("category=doctor")
        .contains("runtime=java")
        .contains("install=ai.narrativetrace%3Anarrativetrace-core%3A0.2.4")
        .contains("step=trap.redaction-proof")
        .contains("language=en")
        .contains("agent=example-cli%20%2F%20example-model");
  }

  @Test
  void carriesTheLabelsTriageSortsOnAndNoLabelAStrangerCouldInvent() {
    String url = IssueFormUrl.of(Reports.complete());

    assertThat(url).contains("labels=from-agent%2Cruntime%3Ajava%2Ccategory%3Adoctor%2Clang%3Aen");
    assertThat(url).doesNotContain("agent%3Aexample-cli");
  }

  @Test
  void neverCarriesTheBodyBecauseTheBodyIsPastedFromTheFile() {
    String url = IssueFormUrl.of(Reports.complete());

    assertThat(url)
        .doesNotContain("did=")
        .doesNotContain("happened=")
        .doesNotContain("expected=")
        .doesNotContain("findings");
  }

  @Test
  void staysUnderTheLengthBudgetEvenWhenEveryShortFieldIsEnormous() {
    String enormous = "x".repeat(10_000);
    String url =
        IssueFormUrl.of(Reports.with(b -> b.step(enormous).install(enormous).language(enormous)));

    assertThat(url.length()).isLessThanOrEqualTo(IssueFormUrl.MAX_LENGTH);
    assertThat(url).contains("template=narrativetrace-report.yml");
  }

  @Test
  void aTruncatedFieldSaysWhereTheRestWentRatherThanEndingMidWord() {
    String url = IssueFormUrl.of(Reports.with(b -> b.step("s".repeat(5_000))));

    assertThat(java.net.URLDecoder.decode(url, java.nio.charset.StandardCharsets.UTF_8))
        .contains(IssueFormUrl.TRUNCATION_MARKER);
  }

  @Test
  void aLabelNeverExceedsWhatTheHostAccepts() {
    String url = IssueFormUrl.of(Reports.with(b -> b.language("x".repeat(10_000))));

    String labels =
        java.net.URLDecoder.decode(url, java.nio.charset.StandardCharsets.UTF_8)
            .replaceAll(".*labels=([^&]*).*", "$1");
    assertThat(labels.split(",")).allSatisfy(label -> assertThat(label.length()).isLessThan(51));
  }

  @Test
  void refusesAReportFromAnotherRuntimeRatherThanFilingItInTheWrongTracker() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> IssueFormUrl.of(Reports.with(b -> b.runtime("python"))))
        .withMessageContaining("python");
  }
}
