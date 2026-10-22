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
 * Two tests in one class, traced through the class rule's context, for {@link
 * SuitePipelineRetentionTest} to run beside {@link SharedPipelineFixtureTwo}: with one pipeline
 * shared by the whole test JVM, each test's file must still hold only its own calls. Its own
 * fixture rather than a reused one, because a {@code @ClassRule} static field captures {@code
 * System.out} when its class loads — a fixture shared with a console-asserting test would make the
 * two tests depend on their order.
 */
public class SharedPipelineFixtureOne {

  @ClassRule public static NarrativeTraceClassRule classRule = new NarrativeTraceClassRule();

  @Rule public NarrativeTraceRule narrativeTrace = classRule.testRule();

  @Test
  public void warehousePacksParcel() {
    narrativeTrace
        .context()
        .enterMethod(new MethodSignature("WarehouseService", "packParcel", List.of()));
    narrativeTrace.context().exitMethodWithReturn("packed");
  }

  @Test
  public void warehouseShipsParcel() {
    narrativeTrace
        .context()
        .enterMethod(new MethodSignature("WarehouseService", "shipParcel", List.of()));
    narrativeTrace.context().exitMethodWithReturn("shipped");
  }
}
