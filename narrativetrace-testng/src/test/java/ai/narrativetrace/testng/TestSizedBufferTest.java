/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The shared ring's size: test-sized by default, configurable, and forgiving of a typo. The shed
 * rows of the JUnit 5 suite stay with core and the JUnit 5 module — the shared pipeline here is
 * built once per JVM, so a four-slot ring cannot be built for one test.
 */
class TestSizedBufferTest {

  @Test
  void theTestDefaultIsSmallerThanTheRuntimeDefault() {
    assertThat(SharedPipeline.DEFAULT_TEST_BUFFER_CAPACITY).isEqualTo(8192);
  }

  @Test
  void aConfiguredCapacityIsHonoured() {
    assertThat(SharedPipeline.bufferCapacityFrom("1024")).isEqualTo(1024);
    assertThat(SharedPipeline.bufferCapacityFrom(" 16 ")).isEqualTo(16);
  }

  @Test
  void anAbsentOrBrokenCapacityDegradesToTheTestDefault() {
    for (var configured : new String[] {null, "", "  ", "lots", "0", "-3"}) {
      assertThat(SharedPipeline.bufferCapacityFrom(configured))
          .as("%s", configured)
          .isEqualTo(SharedPipeline.DEFAULT_TEST_BUFFER_CAPACITY);
    }
  }
}
