/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionsTest {

  @Test
  void earlierSinceIsApplicable() {
    assertTrue(Versions.isApplicable("0.1.0", "0.2.1"));
  }

  @Test
  void equalSinceIsApplicable() {
    assertTrue(Versions.isApplicable("0.2.1", "0.2.1"));
  }

  @Test
  void laterSinceIsNotApplicable() {
    assertFalse(Versions.isApplicable("0.2.2", "0.2.1"));
  }

  @Test
  void comparesNumericallyNotLexicographically() {
    // "0.10.0" lexicographically < "0.2.0" as strings but is numerically later.
    assertFalse(Versions.isApplicable("0.10.0", "0.2.0"));
    assertTrue(Versions.isApplicable("0.2.0", "0.10.0"));
  }
}
