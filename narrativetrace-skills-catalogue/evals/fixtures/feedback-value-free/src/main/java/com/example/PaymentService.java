/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example;

/**
 * This fixture's one service boundary. {@code authToken} is a deny-listed parameter name, so a
 * rendered call of this method is where redaction has to show.
 */
public interface PaymentService {

  String charge(String customerId, String authToken, String amount);
}
