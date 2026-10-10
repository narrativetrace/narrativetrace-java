/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.accounts;

import org.springframework.stereotype.Service;

/** A plain Spring service — nothing here has ever touched NarrativeTrace. */
@Service
public class DefaultAccountService implements AccountService {

  @Override
  public String describeAccount(String accountId) {
    if (accountId == null || accountId.isBlank()) {
      throw new IllegalArgumentException("an account needs an id");
    }
    return "account " + accountId + (isOverdrawn(accountId) ? " is overdrawn" : " is in credit");
  }

  @Override
  public boolean isOverdrawn(String accountId) {
    return accountId.endsWith("-0");
  }
}
