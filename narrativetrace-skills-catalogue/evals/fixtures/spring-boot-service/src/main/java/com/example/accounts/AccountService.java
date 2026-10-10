/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example.accounts;

/** The service boundary an agent is meant to trace — an interface, so a proxy can wrap it. */
public interface AccountService {

  String describeAccount(String accountId);

  boolean isOverdrawn(String accountId);
}
