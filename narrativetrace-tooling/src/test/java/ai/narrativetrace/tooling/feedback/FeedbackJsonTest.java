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

import java.util.List;
import org.junit.jupiter.api.Test;

/** The {@code --json} envelope: one shape per path, every one carrying its own exit code. */
class FeedbackJsonTest {

  private static final String DRAFT_FILE = "build/narrativetrace/feedback/feedback-draft.md";
  private static final String BODY_FILE = "build/narrativetrace/feedback/feedback-body.md";

  @Test
  void aDraftedEnvelopeNamesTheFilesAndTheFactsButNotTheBody() {
    String json = FeedbackJson.drafted(Reports.complete(), DRAFT_FILE, BODY_FILE, "");

    assertThat(json)
        .contains("\"verb\": \"draft\"")
        .contains("\"status\": \"drafted\"")
        .contains("\"runtime\": \"java\"")
        .contains("\"category\": \"doctor\"")
        .contains("\"step\": \"trap.redaction-proof\"")
        .contains("\"draftFile\": \"" + DRAFT_FILE + "\"")
        .contains("\"bodyFile\": \"" + BODY_FILE + "\"")
        .contains("\"exitCode\": 0")
        .doesNotContain("What I did");
  }

  @Test
  void aDraftedEnvelopeCarriesTheNoteWhenATraceWasSkipped() {
    String json =
        FeedbackJson.drafted(Reports.complete(), DRAFT_FILE, BODY_FILE, "a/b.nt breaks vf.email");

    assertThat(json).contains("\"traceNote\": \"a/b.nt breaks vf.email\"");
  }

  @Test
  void aRefusalEnvelopeNamesEveryFieldRuleAndReasonAndExitsTwo() {
    FeedbackDraft.Refused refused =
        new FeedbackDraft.Refused(
            List.of(
                new ValueFreeViolation("happened", ValueFreeRule.RENDERED_CALL),
                new ValueFreeViolation("trace", ValueFreeRule.EMAIL)));

    String json = FeedbackJson.refused("draft", refused);

    assertThat(json)
        .contains("\"verb\": \"draft\"")
        .contains("\"status\": \"refused\"")
        .contains("\"field\": \"happened\"")
        .contains("\"rule\": \"vf.rendered-call\"")
        .contains("\"field\": \"trace\"")
        .contains("\"rule\": \"vf.email\"")
        .contains("\"exitCode\": 2");
  }

  @Test
  void aUrlEnvelopeCarriesTheUrlAndTheFileToPaste() {
    String json = FeedbackJson.url("https://github.com/x/y/issues/new?a=b", BODY_FILE);

    assertThat(json)
        .contains("\"verb\": \"url\"")
        .contains("\"url\": \"https://github.com/x/y/issues/new?a=b\"")
        .contains("\"bodyFile\": \"" + BODY_FILE + "\"")
        .contains("\"exitCode\": 0");
  }

  @Test
  void aGhEnvelopeIsReadyWithACommandOrUnavailableWithAReason() {
    assertThat(FeedbackJson.gh("gh issue create --repo x/y"))
        .contains("\"verb\": \"gh\"")
        .contains("\"status\": \"ready\"")
        .contains("\"command\": \"gh issue create --repo x/y\"")
        .contains("\"exitCode\": 0");

    assertThat(FeedbackJson.ghUnavailable("gh is not authenticated"))
        .contains("\"status\": \"unavailable\"")
        .contains("\"reason\": \"gh is not authenticated\"")
        .contains("\"exitCode\": 1");
  }

  @Test
  void everyEnvelopeEscapesWhatWouldOtherwiseBreakTheJson() {
    String json = FeedbackJson.url("https://x/y?q=\"a\"\n\\b", BODY_FILE);

    assertThat(json).contains("\\\"a\\\"\\n\\\\b");
  }
}
