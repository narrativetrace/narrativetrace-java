/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.core.context.NarrativeContext;
import java.util.List;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Two invocations of one test method, each tracing a different value — the shape the 2026-09-08
 * agent evaluation reduced its artifact-collision report to.
 *
 * <p>Both invocations pass, so both are entitled to their own artifacts; keying the artifact by the
 * method name alone made the second overwrite the first, and the first iteration's evidence was
 * unreachable through the advertised files.
 */
@ExtendWith(NarrativeTraceExtension.class)
class ParameterizedFixture {

  @ParameterizedTest(name = "find {0}")
  @ValueSource(strings = {"KAYAK", "TENT"})
  void equipmentCanBeFound(String sku, NarrativeContext context) {
    context.enterMethod(
        new MethodSignature(
            "CatalogService",
            "findEquipment",
            List.of(new ParameterCapture("sku", "\"" + sku + "\"", false))));
    context.exitMethodWithReturn("\"" + sku + "\"");
  }
}
