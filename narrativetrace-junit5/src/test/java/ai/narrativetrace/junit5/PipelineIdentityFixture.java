/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import ai.narrativetrace.core.context.NarrativeContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Records which {@link NarrativeContext} instance each of its test methods was handed, for {@link
 * PipelineOwnershipFootprintTest} to read back after the nested run completes.
 */
@ExtendWith(NarrativeTraceExtension.class)
class PipelineIdentityFixture {

  static final int TEST_COUNT = 5;
  static final List<Integer> contextIdentities = new ArrayList<>();

  @Test
  void first(NarrativeContext context) {
    contextIdentities.add(System.identityHashCode(context));
  }

  @Test
  void second(NarrativeContext context) {
    contextIdentities.add(System.identityHashCode(context));
  }

  @Test
  void third(NarrativeContext context) {
    contextIdentities.add(System.identityHashCode(context));
  }

  @Test
  void fourth(NarrativeContext context) {
    contextIdentities.add(System.identityHashCode(context));
  }

  @Test
  void fifth(NarrativeContext context) {
    contextIdentities.add(System.identityHashCode(context));
  }
}
