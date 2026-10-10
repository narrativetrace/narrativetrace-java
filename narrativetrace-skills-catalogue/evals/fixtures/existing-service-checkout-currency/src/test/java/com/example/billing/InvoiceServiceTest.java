/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class InvoiceServiceTest {

  private final InvoiceService service = new DefaultInvoiceService();

  @Test
  void issuesAnInvoiceForAnOrder() {
    assertEquals("INV-C-1234-ORD-7-4500", service.issueInvoice("C-1234", "ORD-7", 4500));
  }

  @Test
  void anUnsettledInvoiceIsNotSettled() {
    assertFalse(service.isSettled("INV-C-1234-ORD-7-4500"));
  }
}
