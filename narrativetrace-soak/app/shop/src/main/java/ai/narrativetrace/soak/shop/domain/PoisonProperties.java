/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Names the one deliberately-open validation gate (see README.md "the open-gate design").
 *
 * <p>{@code field} is documentary in P1 — the cart line's {@code quantity} is the field left
 * unvalidated at the code level ({@code CartLineRequest} carries no Bean Validation constraint on
 * it); a later variant that rotates which field is open would need a matching code change, not just
 * a new property value. {@code value} is the sentinel that IS live configuration: {@link
 * ai.narrativetrace.soak.shop.domain.OrderPlacementService} compares every incoming quantity
 * against it to classify a resulting exception as a poison exception (this exact value reached the
 * domain unchecked) rather than an ordinary out-of-stock business failure.
 *
 * @param field name of the field left unvalidated at the edge (documentary in P1)
 * @param value the poison sentinel — deliberately larger than any seeded stock level, so {@link
 *     ai.narrativetrace.examples.ecommerce.InventoryService#reserve} throws unmodified
 */
@ConfigurationProperties(prefix = "soak.poison")
public record PoisonProperties(String field, int value) {

  private static final String DEFAULT_FIELD = "quantity";
  private static final int DEFAULT_VALUE = 2_000_000_000;

  /** Binds a missing property to the documented defaults. */
  public PoisonProperties {
    if (field == null || field.isBlank()) {
      field = DEFAULT_FIELD;
    }
    if (value == 0) {
      value = DEFAULT_VALUE;
    }
  }
}
