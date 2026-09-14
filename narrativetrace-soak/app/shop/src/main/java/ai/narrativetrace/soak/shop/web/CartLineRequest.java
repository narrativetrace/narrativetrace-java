/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.web;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code quantity} deliberately carries no Bean Validation constraint — see README.md "the
 * open-gate design" and {@link ai.narrativetrace.soak.shop.domain.PoisonProperties}.
 */
public record CartLineRequest(@NotBlank String productId, int quantity) {}
