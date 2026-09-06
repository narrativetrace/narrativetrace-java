/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.spring.test;

public class DualInterfaceService implements GreetingService, AuditService {

  @Override
  public String greet(String name) {
    return "Hello, " + name + "!";
  }

  @Override
  public String audit(String action) {
    return "audited: " + action;
  }
}
