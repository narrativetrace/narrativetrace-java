/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import org.testng.annotations.Factory;
import org.testng.annotations.Test;

/** Two instances of one test class made by a {@code @Factory}, each running the same method. */
public class FactoryFixture {

  private final String region;

  public FactoryFixture(String region) {
    this.region = region;
  }

  @Factory
  public static Object[] regions() {
    return new Object[] {new FactoryFixture("north"), new FactoryFixture("south")};
  }

  @Test
  public void regionIsServed() {
    Traced.call("RegionService", "serve", region);
  }
}
