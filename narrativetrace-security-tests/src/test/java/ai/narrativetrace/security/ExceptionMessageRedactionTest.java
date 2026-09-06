/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.render.RedactionPolicy;
import ai.narrativetrace.core.render.ValueRenderer;
import ai.narrativetrace.security.oracle.Emitters;
import ai.narrativetrace.security.oracle.Oracles;
import org.junit.jupiter.api.Test;

/**
 * The channel the value-shape axis did not reach, held to the same standard as every other.
 *
 * <p>INTENT: {@code TraceOutcome.Threw} carries the live {@code Throwable} rather than text a
 * renderer produced, so an exception message passed no redaction axis at all — an adversarial
 * review's threat model called this out. The oracle here is the suite's usual one: a value that
 * must not appear appears in no byte of any emitter's output, artifacts included.
 *
 * <p><b>@edgeCase</b> The claim repaired is <em>parity</em>, not clairvoyance. The value axis
 * matches a whole string — that is what keeps its false-positive budget near zero — so a message
 * that merely mentions a token inside prose is shown, exactly as the identical string is shown when
 * it arrives as a captured parameter. No library can follow a value into {@code "bad token: " +
 * token}, and the annotations guide is careful not to claim it does. What must never differ again
 * is the answer for the <em>same</em> bytes on two channels.
 */
class ExceptionMessageRedactionTest {

  @Test
  void aCredentialShapedStringInAnExceptionMessageIsHiddenLikeAParameterValue() {
    var sentinel = Oracles.freshSentinel();
    var jwt = "eyJhbGciOiJIUzI1NiJ9." + sentinel + ".c2lnbmF0dXJl";

    var outputs = Emitters.everyOutput(Emitters.treeThrowing(new IllegalStateException(jwt)));

    Oracles.containsNoSentinel(outputs, sentinel);
    assertThat(new ValueRenderer().render(jwt))
        .as("the same bytes as a captured parameter — the two channels must agree")
        .isEqualTo(RedactionPolicy.MARKER);
  }

  @Test
  void aCardNumberInAnExceptionMessageReachesNoArtifact() {
    var outputs =
        Emitters.everyOutput(Emitters.treeThrowing(new IllegalStateException("4111111111111111")));

    outputs.forEach(
        (emitter, output) ->
            assertThat(output)
                .as("%s printed the card number", emitter)
                .doesNotContain("41111111"));
  }

  /**
   * The other half of a narrow rule: an order number that fails Luhn is diagnostics, and blanking
   * it would be the over-redaction teams switch a security default off for.
   */
  @Test
  void anOrdinaryMessageStillReachesTheNarrative() {
    var outputs =
        Emitters.everyOutput(
            Emitters.treeThrowing(new IllegalStateException("order 4111111111111112 not found")));

    assertThat(outputs.get("renderer:prose")).contains("order 4111111111111112 not found");
  }
}
