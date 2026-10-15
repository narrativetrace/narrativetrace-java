/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/main/java/com/example/checkout/Order.java
package com.example.checkout;

/** One placed order. A record, so a trace renders it component by component. */
public record Order(String orderId, String customerId, String sku, int quantity, int amountCents) {}
