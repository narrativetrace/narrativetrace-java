/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The edge half of the open-gate design (see {@link
 * ai.narrativetrace.soak.shop.domain.OrderPlacementServiceTest} for the domain half): every field
 * is validated except {@code quantity}, whatever value it carries.
 */
class PlaceOrderRequestValidationTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  private static PlaceOrderRequest requestWith(String customerId, int quantity, String email) {
    return new PlaceOrderRequest(
        customerId,
        List.of(new CartLineRequest("SKU-MECHANICAL-KB", quantity)),
        email,
        "4111111111111111",
        "session-cookie",
        "jwt-token");
  }

  @Test
  void blankCustomerIdIsRejectedAtTheEdge() {
    var violations = validator.validate(requestWith("", 1, "a@b.com"));

    assertThat(violations).isNotEmpty();
  }

  @Test
  void invalidEmailIsRejectedAtTheEdge() {
    var violations = validator.validate(requestWith("C-1234", 1, "not-an-email"));

    assertThat(violations).isNotEmpty();
  }

  @Test
  void emptyLinesAreRejectedAtTheEdge() {
    var request =
        new PlaceOrderRequest(
            "C-1234", List.of(), "a@b.com", "4111111111111111", "session-cookie", "jwt-token");

    assertThat(validator.validate(request)).isNotEmpty();
  }

  @Test
  void thePoisonQuantityPassesEdgeValidationUntouched() {
    // The whole point of the open gate: no value of quantity, however absurd, is rejected here —
    // it reaches the domain unchecked. 2_000_000_000 is PoisonProperties' documented default
    // sentinel (soak.poison.value).
    var violations = validator.validate(requestWith("C-1234", 2_000_000_000, "a@b.com"));

    assertThat(violations).isEmpty();
  }

  @Test
  void aNegativeQuantityAlsoPassesEdgeValidationUntouched() {
    assertThat(validator.validate(requestWith("C-1234", -5, "a@b.com"))).isEmpty();
  }
}
