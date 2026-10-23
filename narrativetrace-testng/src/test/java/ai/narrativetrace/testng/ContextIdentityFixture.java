/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.testng.annotations.Test;

/** Records which context instance each test was handed, for SharedPipelineTest to read back. */
public class ContextIdentityFixture {

  static final List<Integer> IDENTITIES = new CopyOnWriteArrayList<>();

  @Test
  public void first() {
    IDENTITIES.add(System.identityHashCode(NarrativeTraceListener.context()));
  }

  @Test
  public void second() {
    IDENTITIES.add(System.identityHashCode(NarrativeTraceListener.context()));
  }

  @Test
  public void third() {
    IDENTITIES.add(System.identityHashCode(NarrativeTraceListener.context()));
  }
}
