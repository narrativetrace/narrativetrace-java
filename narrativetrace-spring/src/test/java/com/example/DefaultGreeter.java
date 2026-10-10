/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example;

public class DefaultGreeter implements Greeter {
  @Override
  public String greet(String name) {
    return "Hello, " + name + "!";
  }
}
