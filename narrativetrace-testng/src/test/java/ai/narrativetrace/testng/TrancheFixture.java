/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import org.testng.annotations.Test;

/** One traced call named in a trading project's own vocabulary. */
public class TrancheFixture {

  @Test
  public void foldsATranche() {
    Traced.call("TrancheService", "foldTranche", "ok");
  }
}
