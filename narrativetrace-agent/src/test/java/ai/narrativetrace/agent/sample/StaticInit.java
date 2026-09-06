/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent.sample;

public class StaticInit {

  static {
    System.getProperty("test.static.init");
  }

  public int value() {
    return 99;
  }
}
