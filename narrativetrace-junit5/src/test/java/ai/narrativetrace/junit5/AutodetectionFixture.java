/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.core.context.NarrativeContext;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Deliberately carries no {@code @ExtendWith(NarrativeTraceExtension.class)} — {@link
 * ExtensionAutodetectionTest} proves that {@code NarrativeContext} resolution and the aggregate
 * Clarity report still happen when the launcher enables JUnit's own ServiceLoader-based extension
 * autodetection instead of a per-class annotation.
 *
 * <p>Outside that nested launcher the {@code NarrativeContext} parameter cannot resolve, so the
 * fixture runs only when the driving test marks the request with {@link #HARNESS_PARAMETER}.
 * Selected directly — by the module's own test task, an IDE, or PIT's test-class scan, none of
 * which enable autodetection — it reports itself skipped rather than failed, which is what lets it
 * live in the test tree without an exclusion in any build script (PIT aborted the whole module's
 * mutation run on this fixture, nightly 2026-09-24).
 */
@EnabledIf("drivenByHarness")
class AutodetectionFixture {

  /** Launcher configuration parameter the driving test sets to {@code "true"}. */
  static final String HARNESS_PARAMETER = "narrativetrace.junit5.test.fixtureHarness";

  static boolean drivenByHarness(ExtensionContext context) {
    return context
        .getConfigurationParameter(HARNESS_PARAMETER)
        .map(Boolean::parseBoolean)
        .orElse(false);
  }

  @Test
  @DisplayName("warehouse restocks a shelf")
  void warehouseRestocksShelf(NarrativeContext context) {
    context.enterMethod(new MethodSignature("InventoryService", "restockShelf", List.of()));
    context.exitMethodWithReturn("restocked");
  }
}
