/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.opentelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MonotonicToEpochTest {

  private static final Instant ANCHOR = Instant.ofEpochSecond(1_700_000_000L, 500);

  @Test
  void anEventFiveSecondsAfterTheAnchorIsFiveSecondsAfterTheAnchorInstant() {
    var converter = new MonotonicToEpoch(ANCHOR, 123_000L);

    assertThat(converter.epochNanos(123_000L + 5_000_000_000L))
        .isEqualTo(1_700_000_005_000_000_500L);
  }

  @Test
  void anEventAtTheAnchorIsTheAnchorInstant() {
    var converter = new MonotonicToEpoch(ANCHOR, -42L);

    assertThat(converter.epochNanos(-42L)).isEqualTo(1_700_000_000_000_000_500L);
  }

  @Test
  void anEventBeforeTheAnchorHasANegativeOffset() {
    var converter = new MonotonicToEpoch(ANCHOR, 10_000_000_000L);

    assertThat(converter.epochNanos(5_000_000_000L)).isEqualTo(1_699_999_995_000_000_500L);
  }
}
