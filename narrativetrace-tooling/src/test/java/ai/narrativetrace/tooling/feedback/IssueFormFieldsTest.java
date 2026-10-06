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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The issue form and the URL that pre-fills it, held together.
 *
 * <p>INTENT: GitHub documents the field {@code id} as "the canonical identifier for the field in
 * URL query parameter prefills" — and silently ignores a parameter naming an id the form does not
 * have. So a renamed field does not fail anywhere: it produces a form that opens with half its
 * boxes empty, and nobody finds out until a reporter fills them in by hand. This test is the only
 * thing that notices.
 *
 * <p><b>@llmNote</b> The form is read as TEXT rather than parsed as YAML, because this library
 * declares zero dependencies and the question is a flat one: which ids does the file declare. If
 * the question ever becomes structural (which validations, which options), the test moves to a
 * module that may have a YAML parser rather than growing a parser here.
 *
 * <p><b>@llmNote</b> The form file is declared as an input of this module's test task. Without
 * that, editing only the form leaves this test UP-TO-DATE and unrun inside {@code check} — the
 * repo-reading-test hole that has bitten the drift gates in the skills catalogue.
 */
class IssueFormFieldsTest {

  private static final Path FORM =
      Path.of(System.getProperty("projectDir", "."))
          .resolve(".github/ISSUE_TEMPLATE/" + PublicRepository.FORM);

  private static final Pattern ID_LINE = Pattern.compile("^\\s*id: (\\S+)$", Pattern.MULTILINE);

  private static final Pattern URL_PARAMETER = Pattern.compile("[?&]([^=&]+)=");

  /** Parameters the URL carries that are GitHub's own, not form field ids. */
  private static final List<String> RESERVED = List.of("template", "title", "labels");

  @Test
  void theFormIsWhereTheUrlSaysItIs() {
    assertThat(FORM).as("%s must exist — the URL names it in every report", FORM).exists();
  }

  @Test
  void everyFieldTheUrlPreFillsIsAFieldTheFormDeclares() {
    List<String> formIds = ids();

    assertThat(preFilledParameters())
        .as("a parameter naming an id the form does not have is silently ignored by GitHub")
        .isSubsetOf(formIds);
  }

  @Test
  void everyFieldTheReportCanFillIsOnTheForm() {
    assertThat(ids())
        .contains("category", "runtime", "install", "step", "did", "happened", "expected")
        .contains("language", "agent")
        .as("the box the body file is pasted into, and the attestation that gates submitting")
        .contains("report", "reviewed");
  }

  @Test
  void theFormOffersExactlyTheCategoriesTheVerbAccepts() {
    String form = read();

    for (FeedbackCategory category : FeedbackCategory.values()) {
      assertThat(form)
          .as("the dropdown must offer %s, or a drafted report cannot pre-fill it", category.id())
          .contains("- " + category.id());
    }
  }

  @Test
  void theFormCarriesTheFromAgentLabelAndTheAttestationIsRequired() {
    String form = read();

    assertThat(form).contains("- from-agent");
    assertThat(form).contains("no values from my traces");
    assertThat(form).contains("required: true");
  }

  @Test
  void theFormTellsAReporterThatFilingIsPublic() {
    assertThat(read())
        .as("the privacy note is the form's own, for somebody who arrived without the verb")
        .contains("This issue is public");
  }

  @Test
  void theFormNeverAsksForAnArtifactThatCarriesValues() {
    assertThat(read()).contains("Never paste a rendered narrative, a log file or a source file");
  }

  private static List<String> preFilledParameters() {
    Matcher matcher = URL_PARAMETER.matcher(IssueFormUrl.of(Reports.complete()));
    List<String> parameters = new ArrayList<>();
    while (matcher.find()) {
      if (!RESERVED.contains(matcher.group(1))) {
        parameters.add(matcher.group(1));
      }
    }
    return parameters;
  }

  private static List<String> ids() {
    Matcher matcher = ID_LINE.matcher(read());
    List<String> ids = new ArrayList<>();
    while (matcher.find()) {
      ids.add(matcher.group(1));
    }
    return ids;
  }

  private static String read() {
    try {
      return Files.readString(FORM);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + FORM, e);
    }
  }
}
