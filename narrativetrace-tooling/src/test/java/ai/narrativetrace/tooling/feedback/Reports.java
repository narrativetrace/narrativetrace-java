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

import java.util.function.Consumer;

/**
 * One complete, value-free report every feedback test starts from, and a one-field override.
 *
 * <p>Shaped after {@code DoctorSnapshot.healthy()}: a case says what is DIFFERENT about it, so a
 * test reads as the one decision it is about rather than as twelve constructor arguments.
 *
 * <p><b>@llmNote</b> {@link #DOCTOR_JSON} carries {@code RedactionProofCheck}'s OWN message and fix
 * text, byte for byte, including the {@code "[REDACTED]"} literal they quote. An earlier version of
 * this helper paraphrased them — "no test proves a sensitive value is redacted" — and that
 * paraphrase is why nothing noticed that the gate refused every doctor report there is. A
 * hand-written stand-in for a GENERATED artifact must carry the generator's own bytes, or it dodges
 * exactly the defect it exists to expose.
 */
final class Reports {

  static final String DOCTOR_JSON =
      """
{
  "findings": [
    {
      "id": "trap.redaction-proof",
      "status": "fail",
      "message": "No test file asserts the literal \\"[REDACTED]\\"",
      "fix": "Render a call with a deny-listed parameter name (password, token, secret, ...), assert the rendered text contains \\"[REDACTED]\\", and assert a neighboring, non-sensitive value is still present.",
      "docUrl": "https://narrativetrace.ai/docs/privacy-and-redaction",
      "skill": "narrativetrace-doctor"
    }
  ],
  "exitCode": 1
}
""";

  static final String STRUCTURAL_TRACE =
      """
      scenario: Order is placed

      - OrderService.placeOrder(customerId, total)
        - Inventory.reserve(sku) → value
      """;

  private Reports() {}

  static FeedbackReport complete() {
    return builder().build();
  }

  static FeedbackReport with(Consumer<FeedbackReport.Builder> override) {
    FeedbackReport.Builder builder = builder();
    override.accept(builder);
    return builder.build();
  }

  static FeedbackReport.Builder builder() {
    return FeedbackReport.builder()
        .runtime("java")
        .category(FeedbackCategory.DOCTOR)
        .install("ai.narrativetrace:narrativetrace-core:0.2.4")
        .step("trap.redaction-proof")
        .narrative(
            new ProblemNarrative(
                "ran the doctor, applied the fix it printed, ran it again",
                "the same check still failed, with the same message",
                "the check to pass once the test asserts the marker"))
        .language("en")
        .agent(new AgentIdentity("example-cli", "example-model"))
        .attachments(new Attachments(DOCTOR_JSON, "", STRUCTURAL_TRACE));
  }
}
