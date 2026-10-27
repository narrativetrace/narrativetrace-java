/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import ai.narrativetrace.api.annotation.DeepTrace;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/** The usual pairing: the class rule owns the agent bridge, the test rule opens the scope. */
@DeepTrace
public class DeepLinkedJunit4Fixture {

  @ClassRule public static NarrativeTraceClassRule classRule = new NarrativeTraceClassRule();

  @Rule public NarrativeTraceRule narrativeTrace = classRule.testRule();

  @Test
  public void linked() {
    DeepJunit4Fixture.priceWithTax(narrativeTrace.context());
  }
}
