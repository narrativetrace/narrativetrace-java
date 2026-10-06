/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

/**
 * Feature-combination coverage for {@link InstallerTaskOptions}: what a caller sees when more than
 * one flag is invalid at the same time.
 */
class InstallerTaskOptionsAdversarialM3Test {

  /**
   * A caller who mistypes BOTH flags at once is told about the first, exactly like {@code
   * InstallerArguments}'s "first error wins" rule at the launcher. {@code InstallerTaskOptions}
   * checks them in two explicit statements so that this is a decision rather than a side effect of
   * the order Java evaluates a record constructor's arguments in.
   */
  @Test
  void whenBothOnlyAndVendorAreInvalidTheOnlyErrorSurfacesFirst() {
    assertThatThrownBy(() -> InstallerTaskOptions.from(false, false, false, "everything", "cursor"))
        .isInstanceOf(GradleException.class)
        .hasMessageContaining("--only")
        .hasMessageNotContaining("--vendor");
  }
}
