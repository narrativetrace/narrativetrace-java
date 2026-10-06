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

import java.util.Map;

/**
 * Turns a gathered {@link FeedbackReport} into something that may be shown to a person — or into a
 * refusal that names what is in the way.
 *
 * <p>INTENT: three things in one place, in this order, because the order is the safety property.
 * Normalise what can be normalised (a home path becomes {@code ~}); run every {@link ValueFreeRule}
 * over every field that would be filed; render the two texts ONLY if nothing refused. A caller
 * cannot reach a URL or a body file past a violation, because {@link FeedbackDraft.Refused} has
 * neither.
 *
 * <p><b>@sideEffects</b> None. This returns text; writing it to disk is the entry point's job, and
 * that separation is what lets every test here be hermetic.
 */
public final class FeedbackDrafter {

  private FeedbackDrafter() {}

  /**
   * Normalise, gate, and render.
   *
   * @throws IllegalArgumentException when {@code report} is null
   */
  public static FeedbackDraft draft(FeedbackReport report) {
    if (report == null) {
      throw new IllegalArgumentException("there is nothing to draft from a null report");
    }
    FeedbackReport normalised = normalise(report);
    Map<String, String> fields = normalised.fields();
    var violations = ValueFreeCheck.violations(fields);
    if (!violations.isEmpty()) {
      return new FeedbackDraft.Refused(violations);
    }
    String body = FeedbackRender.body(normalised);
    return new FeedbackDraft.Drafted(normalised, FeedbackRender.draft(normalised, body), body);
  }

  /** Every text field with its home directories rewritten to {@code ~}. */
  private static FeedbackReport normalise(FeedbackReport report) {
    ProblemNarrative narrative = report.narrative();
    Attachments attachments = report.attachments();
    return report.toBuilder()
        .install(HomePaths.toTilde(report.install()))
        .step(HomePaths.toTilde(report.step()))
        .narrative(
            new ProblemNarrative(
                HomePaths.toTilde(narrative.did()),
                HomePaths.toTilde(narrative.happened()),
                HomePaths.toTilde(narrative.expected())))
        .attachments(
            new Attachments(
                HomePaths.toTilde(attachments.doctorReport()),
                HomePaths.toTilde(attachments.doctorUnavailable()),
                HomePaths.toTilde(attachments.structuralTrace())))
        .build();
  }
}
