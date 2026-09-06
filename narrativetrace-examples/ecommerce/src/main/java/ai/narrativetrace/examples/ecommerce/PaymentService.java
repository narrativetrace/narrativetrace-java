/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import ai.narrativetrace.api.annotation.NotTraced;
import ai.narrativetrace.api.annotation.OnError;

public interface PaymentService {
  @OnError(
      value = "Payment declined for customer {customerId}, amount was {amount}",
      exception = PaymentDeclinedException.class)
  PaymentConfirmation charge(String customerId, double amount, @NotTraced String cardToken);
}
