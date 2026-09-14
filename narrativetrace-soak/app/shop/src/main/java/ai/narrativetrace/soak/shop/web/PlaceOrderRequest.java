/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Every field is validated at the edge except each line's {@code quantity} (see {@link
 * CartLineRequest}) — carries seeded PII ({@code email}, {@code cardNumber}, {@code sessionCookie},
 * {@code jwt}) so NarrativeTrace's default redaction has real values to mask.
 */
public record PlaceOrderRequest(
    @NotBlank String customerId,
    @NotEmpty @Size(max = 20) List<@Valid CartLineRequest> lines,
    @Email @NotBlank String email,
    @NotBlank String cardNumber,
    @NotBlank String sessionCookie,
    @NotBlank String jwt) {}
