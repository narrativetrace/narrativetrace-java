/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/** Registers the listener explicitly, as a project without service-loader listeners does. */
@Listeners(NarrativeTraceListener.class)
public class AnnotatedFixture {

  @Test
  public void parcelIsWeighed() {
    Traced.call("ScaleService", "weigh", "2kg");
  }

  @Test
  public void parcelIsLabelled() {
    Traced.call("LabelService", "print", "label-1");
  }
}
