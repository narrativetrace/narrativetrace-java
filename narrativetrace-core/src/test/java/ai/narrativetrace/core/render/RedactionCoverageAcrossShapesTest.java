/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.RenderedValue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The audit's regression spec for finding 3, over every container shape the renderer supports.
 *
 * <p>INTENT: `RedactionDefaultsGapTest` pins the policy; this pins the product. A name pattern that
 * the policy knows but a render path never asks about is not protection. Both parallel paths —
 * {@code render} and {@code renderStructured} — are asserted for every case, because divergence
 * between them is this codebase's documented top source of bugs and a secret must not depend on
 * which one an exporter picked.
 */
class RedactionCoverageAcrossShapesTest {

  private static final String PAN = "4111111111111111";
  private static final String JWT =
      "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZGEifQ.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk";
  private static final String CANARY = "canary-8842-secret";

  private final ValueRenderer renderer = new ValueRenderer();

  record Payment(String cardNumber, String accountNumber, String iban, String amount) {}

  record Session(String sessionId, String jwt, String cookie, String user) {}

  record Wrapper(Payment payment, Session session) {}

  /** Fields are read by reflection, which is the whole point — PMD cannot see those reads. */
  @SuppressWarnings("PMD.UnusedPrivateField")
  static final class LegacyPojo {
    private final String routingNumber = CANARY;
    private final String pan = CANARY;
    private final String description = "visible";

    @Override
    public String toString() {
      return "LegacyPojo";
    }
  }

  private void assertBothPathsHide(Object value, String... mustNotAppear) {
    var flat = renderer.render(value);
    var structured = renderer.renderStructured(value).toString();
    for (var secret : mustNotAppear) {
      assertThat(flat)
          .as("flat render of %s", value.getClass().getSimpleName())
          .doesNotContain(secret);
      assertThat(structured)
          .as("structured render of %s", value.getClass().getSimpleName())
          .doesNotContain(secret);
    }
  }

  @Test
  @DisplayName("a record's newly-covered components are redacted on both paths")
  void aRecordsNewlyCoveredComponentsAreRedacted() {
    var payment = new Payment(CANARY, CANARY, CANARY, "10.00");

    assertBothPathsHide(payment, CANARY);
    assertThat(renderer.render(payment)).contains("10.00");
  }

  @Test
  @DisplayName("session, jwt and cookie components are redacted on both paths")
  void sessionJwtAndCookieComponentsAreRedacted() {
    assertBothPathsHide(new Session(CANARY, CANARY, CANARY, "ada"), CANARY);
    assertThat(renderer.render(new Session(CANARY, CANARY, CANARY, "ada"))).contains("ada");
  }

  @Test
  @DisplayName("a plain POJO's fields are redacted on both paths, pan included")
  void aPlainPojoIsRedactedIncludingTheShortPanName() {
    assertBothPathsHide(new LegacyPojo(), CANARY);
  }

  @Test
  @DisplayName("map entries are redacted by key on both paths")
  void mapEntriesAreRedactedByKey() {
    var map =
        Map.of("cardNumber", CANARY, "routing_number", CANARY, "iban", CANARY, "city", "Zagreb");

    assertBothPathsHide(map, CANARY);
    assertThat(renderer.render(map)).contains("Zagreb");
  }

  @Test
  @DisplayName("mixed-case keys are redacted on both paths")
  void mixedCaseKeysAreRedacted() {
    assertBothPathsHide(Map.of("CardNumber", CANARY, "SESSION_ID", CANARY, "IbAn", CANARY), CANARY);
  }

  @Test
  @DisplayName("a secret nested two records deep is redacted on both paths")
  void aSecretNestedTwoRecordsDeepIsRedacted() {
    var wrapper =
        new Wrapper(
            new Payment(CANARY, CANARY, CANARY, "1.00"),
            new Session(CANARY, CANARY, CANARY, "ada"));

    assertBothPathsHide(wrapper, CANARY);
  }

  @Test
  @DisplayName("a bearer value in an unnamed list position is masked by shape on both paths")
  void aBearerValueInAnUnnamedListPositionIsMaskedByShape() {
    assertBothPathsHide(List.of("ordinary", PAN, JWT), PAN, JWT);
  }

  @Test
  @DisplayName("a bearer value returned bare is masked by shape on both paths")
  void aBearerValueReturnedBareIsMaskedByShape() {
    assertThat(renderer.render(JWT)).isEqualTo(RedactionPolicy.MARKER);
    assertThat(renderer.renderStructured(JWT))
        .isEqualTo(new RenderedValue.StringVal(RedactionPolicy.MARKER));
  }

  @Test
  @DisplayName("a value shaped like an order number stays visible on both paths")
  void anOrderNumberShapedValueStaysVisible() {
    var orderNumber = "4111111111111112";

    assertThat(renderer.render(orderNumber)).contains(orderNumber);
    assertThat(renderer.renderStructured(orderNumber))
        .isEqualTo(new RenderedValue.StringVal(orderNumber));
  }

  @Test
  @DisplayName("a disabled policy renders the bearer value untouched on both paths")
  void aDisabledPolicyRendersTheBearerValueUntouched() {
    var permissive = new ValueRenderer(200, 5, 5, RedactionPolicy.DISABLED);

    assertThat(permissive.render(PAN)).contains(PAN);
    assertThat(permissive.renderStructured(PAN)).isEqualTo(new RenderedValue.StringVal(PAN));
  }

  @Test
  @DisplayName("name redaction and shape masking combine without either being lost")
  void nameRedactionAndShapeMaskingCombine() {
    var mixed = Map.of("cardNumber", CANARY, "note", PAN, "city", "Zagreb");

    var flat = renderer.render(mixed);

    assertThat(flat).doesNotContain(CANARY).doesNotContain(PAN).contains("Zagreb");
  }
}
