/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import ai.narrativetrace.api.annotation.DeepTrace;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** Depth requested once, on the class; a set-up method runs before each test. */
@DeepTrace
public class ClassDeepFixture {

  @BeforeMethod
  public void setUp() {
    Traced.call("Pricing", "prepare", "ready");
  }

  @Test
  public void anyTest() {
    Traced.call("A", "b", "1");
  }
}
