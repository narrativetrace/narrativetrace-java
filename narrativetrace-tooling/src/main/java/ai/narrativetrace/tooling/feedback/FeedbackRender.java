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

/**
 * The two texts a cleared report becomes: the body that gets filed, and the draft a person reads
 * before deciding to file it.
 *
 * <p>INTENT: one renderer, so the draft cannot say something the body does not. The draft CONTAINS
 * the body verbatim — {@link FeedbackDraft.Drafted} refuses to exist otherwise — because approving
 * a draft has to mean approving what is filed, byte for byte, and a summary is not that.
 *
 * <p><b>@llmNote</b> Fences are four backticks, not three. An attached doctor report or structural
 * trace is somebody else's text, and a three-backtick fence around text that happens to contain
 * three backticks ends the block early and spills the rest into the issue as prose.
 */
public final class FeedbackRender {

  /** The note the draft closes with — what filing publicly actually means (design D5). */
  public static final String PRIVACY_NOTE =
      "Filing on GitHub is public, under your own account, and shows that this project uses"
          + " NarrativeTrace. Nothing is sent anywhere until you choose to file it.";

  private static final String FENCE = "````";

  private FeedbackRender() {}

  /** The report in Markdown: what gets filed, with nothing in it about the process. */
  public static String body(FeedbackReport report) {
    StringBuilder out = new StringBuilder();
    appendFacts(out, report);
    appendNarrative(out, report.narrative());
    appendAttachments(out, report.attachments());
    return out.toString();
  }

  /** The body, framed by what a person needs in order to decide: the two choices and the note. */
  public static String draft(FeedbackReport report, String body) {
    return "# NarrativeTrace problem report (draft — nothing has been filed)\n\n"
        + body
        + "\n---\n\n"
        + PRIVACY_NOTE
        + "\n";
  }

  private static void appendFacts(StringBuilder out, FeedbackReport report) {
    out.append("- runtime: ")
        .append(report.runtime())
        .append("\n- category: ")
        .append(report.category().id())
        .append("\n- install: ")
        .append(report.install())
        .append("\n- step: ")
        .append(report.step())
        .append("\n- language: ")
        .append(report.language())
        .append("\n- agent: ")
        .append(report.agent().describe().isEmpty() ? "not reported" : report.agent().describe())
        .append("\n\n");
  }

  private static void appendNarrative(StringBuilder out, ProblemNarrative narrative) {
    out.append("## What I did\n\n")
        .append(narrative.did())
        .append("\n\n## What happened\n\n")
        .append(narrative.happened())
        .append("\n\n## What I expected\n\n")
        .append(narrative.expected())
        .append("\n\n");
  }

  private static void appendAttachments(StringBuilder out, Attachments attachments) {
    out.append("## Doctor report\n\n");
    if (attachments.hasDoctorReport()) {
      out.append(FENCE)
          .append("json\n")
          .append(attachments.doctorReport().strip())
          .append('\n')
          .append(FENCE)
          .append("\n\n");
    } else {
      out.append("No doctor report: ").append(attachments.doctorUnavailable()).append("\n\n");
    }
    out.append("## Structural trace\n\n");
    if (attachments.hasStructuralTrace()) {
      out.append(FENCE)
          .append("\n")
          .append(attachments.structuralTrace().strip())
          .append('\n')
          .append(FENCE)
          .append('\n');
    } else {
      out.append("No structural trace was attached.\n");
    }
  }
}
