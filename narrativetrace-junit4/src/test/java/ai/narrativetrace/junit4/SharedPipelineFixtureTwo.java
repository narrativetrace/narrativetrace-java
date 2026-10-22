/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import ai.narrativetrace.api.event.MethodSignature;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * The second class of {@link SuitePipelineRetentionTest}'s pair — see {@link
 * SharedPipelineFixtureOne}.
 */
public class SharedPipelineFixtureTwo {

  @ClassRule public static NarrativeTraceClassRule classRule = new NarrativeTraceClassRule();

  @Rule public NarrativeTraceRule narrativeTrace = classRule.testRule();

  @Test
  public void courierCollectsParcel() {
    narrativeTrace
        .context()
        .enterMethod(new MethodSignature("CourierService", "collectParcel", List.of()));
    narrativeTrace.context().exitMethodWithReturn("collected");
  }
}
