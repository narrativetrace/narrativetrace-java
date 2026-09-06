/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.spi.TraceEventListener;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PipelineSpecTest {

  private static final PipelineSpec.Settings NO_SETTINGS = (key, defaultValue) -> defaultValue;

  @Test
  void allowsNoDurableListenerBecauseSomeTopologiesRelinquishThatPath() {
    var spec = new PipelineSpec(null, List.of(), NO_SETTINGS);

    assertThat(spec.durableListener()).isNull();
    assertThat(spec.listeners()).isEmpty();
  }

  @Test
  void copiesTheListenerListSoLaterCallerMutationCannotReachTheFactory() {
    var mutable = new ArrayList<TraceEventListener>();
    mutable.add(event -> {});
    var spec = new PipelineSpec(null, mutable, NO_SETTINGS);

    mutable.clear();

    assertThat(spec.listeners()).hasSize(1);
  }

  @Test
  void settingsResolveThroughTheSuppliedLookup() {
    var spec =
        new PipelineSpec(
            null, List.of(), (key, defaultValue) -> "ringSize".equals(key) ? "1024" : defaultValue);

    assertThat(spec.settings().get("ringSize", "unset")).isEqualTo("1024");
    assertThat(spec.settings().get("other", "fallback")).isEqualTo("fallback");
  }

  @Test
  void rejectsNullListeners() {
    assertThatThrownBy(() -> new PipelineSpec(null, null, NO_SETTINGS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("listeners");
  }

  @Test
  void rejectsNullSettings() {
    assertThatThrownBy(() -> new PipelineSpec(null, List.of(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("settings");
  }
}
