/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.accounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AccountServiceTest {

  private final AccountService service = new DefaultAccountService();

  @Test
  void describesAnAccountInCredit() {
    assertEquals("account ACC-17 is in credit", service.describeAccount("ACC-17"));
  }

  @Test
  void anAccountEndingInZeroIsOverdrawn() {
    assertTrue(service.isOverdrawn("ACC-0"));
  }
}
