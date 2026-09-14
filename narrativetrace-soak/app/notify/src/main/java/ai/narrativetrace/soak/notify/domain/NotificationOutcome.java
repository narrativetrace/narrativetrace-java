/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.notify.domain;

/**
 * Result of processing one notification.
 *
 * @param customerId the customer the order confirmation was for
 * @param orderId the order the confirmation names
 * @param simulatedDelayMillis how long the processor slept before answering
 */
public record NotificationOutcome(String customerId, String orderId, long simulatedDelayMillis) {}
