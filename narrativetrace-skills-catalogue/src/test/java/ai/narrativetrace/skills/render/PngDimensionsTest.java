/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.skills.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PngDimensionsTest {

  @Test
  void readsWidthAndHeightFromAFlatPng() {
    byte[] png = PngFixtures.flat(64, 32, (byte) 0x4a);

    assertThat(PngDimensions.width(png)).isEqualTo(64);
    assertThat(PngDimensions.height(png)).isEqualTo(32);
  }

  @Test
  void rejectsBytesTooShortToHoldASignature() {
    byte[] tooShort = new byte[10];

    assertThatThrownBy(() -> PngDimensions.width(tooShort))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBytesWithTheWrongSignature() {
    byte[] bogus = new byte[24];
    bogus[0] = 1;

    assertThatThrownBy(() -> PngDimensions.height(bogus))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
