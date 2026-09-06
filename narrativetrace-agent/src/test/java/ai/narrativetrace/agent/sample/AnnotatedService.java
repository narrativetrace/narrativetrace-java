/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent.sample;

import ai.narrativetrace.api.annotation.Narrated;
import ai.narrativetrace.api.annotation.NotTraced;
import ai.narrativetrace.api.annotation.OnError;

public class AnnotatedService {

  public String login(String username, @NotTraced String password) {
    return "token-for-" + username;
  }

  @Narrated("Authenticating {username} with password {password}")
  @OnError("Authentication failed for {username} with password {password}")
  public String authenticate(String username, @NotTraced String password) {
    if (!"correct-horse".equals(password)) {
      throw new IllegalArgumentException("bad credentials");
    }
    return "token-for-" + username;
  }

  @Narrated("Processing {item} for quantity {quantity}")
  public double calculatePrice(String item, int quantity) {
    return quantity * 9.99;
  }

  @OnError(
      value = "Transfer failed for amount {amount}",
      exception = IllegalArgumentException.class)
  @OnError(value = "Transfer error for amount {amount}", exception = Throwable.class)
  public void transfer(String from, String to, int amount) {
    if (amount < 0) {
      throw new IllegalArgumentException("Negative amount: " + amount);
    }
  }

  @OnError("Lookup failed for id {id}")
  public String lookup(String id) {
    if (id == null) {
      throw new IllegalArgumentException("null id");
    }
    return "found-" + id;
  }
}
