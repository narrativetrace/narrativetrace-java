/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.notify;

import jakarta.validation.constraints.NotBlank;

/** Mirrors the body {@code JsonPlaceholderNotificationService} would have posted. */
public record NotificationRequest(
    @NotBlank String customerId, @NotBlank String orderId, String type) {}
