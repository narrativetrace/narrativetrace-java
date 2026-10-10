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

import org.junit.jupiter.api.Test;

/** One rule at a time: what it must reject, and the near miss it must let through. */
class ValueFreeRuleTest {

  @Test
  void renderedCallRejectsAParameterCarryingItsValue() {
    assertThat(
            ValueFreeRule.RENDERED_CALL.rejects("OrderService.placeOrder(customerId: \"C-1234\")"))
        .isTrue();
  }

  @Test
  void renderedCallAcceptsTheStructuralFormOfTheSameCall() {
    assertThat(ValueFreeRule.RENDERED_CALL.rejects("OrderService.placeOrder(customerId, total)"))
        .isFalse();
  }

  @Test
  void renderedCallRejectsEveryMarkdownEmphasisBetweenNameAndParenthesis() {
    for (String mark : new String[] {"**", "__", "*", "_"}) {
      assertThat(
              ValueFreeRule.RENDERED_CALL.rejects(
                  "- " + mark + "Order.place" + mark + "(customerId: `\"C-1\"`)"))
          .as("emphasis %s", mark)
          .isTrue();
    }
  }

  @Test
  void renderedCallAcceptsTheStructuralFormWhenTheNameIsEmphasised() {
    assertThat(ValueFreeRule.RENDERED_CALL.rejects("- **Order.place**(customerId, total)"))
        .isFalse();
  }

  @Test
  void renderedOutcomeRejectsAReturnedValue() {
    assertThat(
            ValueFreeRule.RENDERED_OUTCOME.rejects("OrderService.placeOrder(c) \u2192 \"ORD-9\""))
        .isTrue();
  }

  @Test
  void renderedOutcomeAcceptsTheStructuralOutcomeMarkers() {
    assertThat(ValueFreeRule.RENDERED_OUTCOME.rejects("OrderService.placeOrder(c) \u2192 value"))
        .isFalse();
    assertThat(ValueFreeRule.RENDERED_OUTCOME.rejects("OrderService.placeOrder(c) !! IllegalState"))
        .isFalse();
    assertThat(ValueFreeRule.RENDERED_OUTCOME.rejects("OrderService.placeOrder(c) ?? incomplete"))
        .isFalse();
  }

  @Test
  void durationRejectsARenderedElapsedTime() {
    assertThat(ValueFreeRule.DURATION.rejects("OrderService.placeOrder(c) \u2192 value \u2014 1ms"))
        .isTrue();
    assertThat(ValueFreeRule.DURATION.rejects("settle(c) \u2014 1.5 s")).isTrue();
  }

  @Test
  void durationAcceptsAVersionAndAnOrdinaryNumber() {
    assertThat(ValueFreeRule.DURATION.rejects("ai.narrativetrace:narrativetrace-core:0.2.4"))
        .isFalse();
    assertThat(ValueFreeRule.DURATION.rejects("step 3 \u2014 the doctor reported 2 findings"))
        .isFalse();
  }

  @Test
  void markerRejectsTheRedactionMarkerBecauseOnlyARenderedTraceCarriesIt() {
    assertThat(ValueFreeRule.MARKER.rejects("Login.authenticate(password: [REDACTED])")).isTrue();
  }

  @Test
  void markerAcceptsProseThatMerelyTalksAboutRedaction() {
    assertThat(ValueFreeRule.MARKER.rejects("redaction is never proven in a test")).isFalse();
  }

  @Test
  void namedSecretRejectsADenyListedNameCarryingAValue() {
    assertThat(ValueFreeRule.NAMED_SECRET.rejects("Authorization: Bearer abc")).isTrue();
    assertThat(ValueFreeRule.NAMED_SECRET.rejects("userPassword=hunter2")).isTrue();
    assertThat(ValueFreeRule.NAMED_SECRET.rejects("contrase\u00f1a: ada")).isTrue();
  }

  @Test
  void namedSecretAcceptsADenyListedWordThatIsNotTheKeyBeingAssigned() {
    assertThat(ValueFreeRule.NAMED_SECRET.rejects("the authorization header: it never arrived"))
        .isFalse();
    assertThat(ValueFreeRule.NAMED_SECRET.rejects("Login.authenticate(password)")).isFalse();
  }

  @Test
  void valueShapeRejectsACredentialPrefixAndASecretShapedValue() {
    assertThat(ValueFreeRule.VALUE_SHAPE.rejects("ghp_0123456789abcdefghij")).isTrue();
    assertThat(ValueFreeRule.VALUE_SHAPE.rejects("AKIAIOSFODNN7EXAMPLE")).isTrue();
    assertThat(ValueFreeRule.VALUE_SHAPE.rejects("-----BEGIN RSA PRIVATE KEY-----")).isTrue();
    assertThat(ValueFreeRule.VALUE_SHAPE.rejects("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZGEifQ.dBjft"))
        .isTrue();
    assertThat(ValueFreeRule.VALUE_SHAPE.rejects("the id was 4111111111111111")).isTrue();
  }

  @Test
  void valueShapeAcceptsADateAVersionAndAShortIdentifier() {
    assertThat(ValueFreeRule.VALUE_SHAPE.rejects("on 2026-09-02 the doctor reported it")).isFalse();
    assertThat(ValueFreeRule.VALUE_SHAPE.rejects("ai.narrativetrace:narrativetrace-core:0.2.4"))
        .isFalse();
    assertThat(ValueFreeRule.VALUE_SHAPE.rejects("invoice 987654321")).isFalse();
  }

  @Test
  void entropyRejectsAHighEntropyBase64RunAndALongHexRun() {
    assertThat(ValueFreeRule.ENTROPY.rejects("dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        .isTrue();
    assertThat(ValueFreeRule.ENTROPY.rejects("a3f5c8e9d2b14706a3f5c8e9d2b1470689abcdef01234567"))
        .isTrue();
  }

  @Test
  void entropyAcceptsHyphenatedProseAndAShortHexRun() {
    assertThat(ValueFreeRule.ENTROPY.rejects("the-quick-brown-fox-jumps-over-the-lazy-dog"))
        .isFalse();
    assertThat(ValueFreeRule.ENTROPY.rejects("deadbeef is eight hex characters")).isFalse();
  }

  @Test
  void emailRejectsAnAddressAndAcceptsACoordinateThatMerelyHasDots() {
    assertThat(ValueFreeRule.EMAIL.rejects("reported by ada@example.com")).isTrue();
    assertThat(ValueFreeRule.EMAIL.rejects("ai.narrativetrace:narrativetrace-core:0.2.4"))
        .isFalse();
    assertThat(ValueFreeRule.EMAIL.rejects("the @NotTraced annotation was imported")).isFalse();
  }

  @Test
  void homePathRejectsEveryPlatformsHomeDirectoryAndAcceptsTheRewrittenForm() {
    assertThat(ValueFreeRule.HOME_PATH.rejects("/Users/ada/work/orders")).isTrue();
    assertThat(ValueFreeRule.HOME_PATH.rejects("/home/ada/work/orders")).isTrue();
    assertThat(ValueFreeRule.HOME_PATH.rejects("C:\\Users\\ada\\work")).isTrue();
    assertThat(ValueFreeRule.HOME_PATH.rejects("~/work/orders")).isFalse();
    assertThat(ValueFreeRule.HOME_PATH.rejects("build/narrativetrace/doctor-report.json"))
        .isFalse();
  }

  @Test
  void controlRejectsAControlCharacterAndAcceptsTheTwoAnArtifactUses() {
    assertThat(ValueFreeRule.CONTROL.rejects("a\u0000b")).isTrue();
    assertThat(ValueFreeRule.CONTROL.rejects("a\u001bb")).isTrue();
    assertThat(ValueFreeRule.CONTROL.rejects("a\rb")).isTrue();
    assertThat(ValueFreeRule.CONTROL.rejects("a\nb\tc")).isFalse();
  }

  /**
   * The overlapping-match bug: consuming the value's first character made the scan resume INSIDE
   * the next key, so an indented paste read {@code password} as {@code assword} and passed.
   */
  @Test
  void namedSecretRejectsADenyListedKeyThatFollowsAnotherKeyOnTheNextLine() {
    assertThat(ValueFreeRule.NAMED_SECRET.rejects("spring:\n  datasource:\n    password: hunter2"))
        .isTrue();
  }

  /**
   * A bidi override reorders what the person triaging the issue sees; it is a control character.
   */
  @Test
  void controlRejectsABidiOverrideButNotAZeroWidthJoinerALanguageNeeds() {
    assertThat(ValueFreeRule.CONTROL.rejects("trap.\u202eredaction-proof")).isTrue();
    assertThat(ValueFreeRule.CONTROL.rejects("\ufeffleading byte order mark")).isTrue();
    assertThat(ValueFreeRule.CONTROL.rejects("\u0915\u094d\u200d\u0937")).isFalse();
  }
}
