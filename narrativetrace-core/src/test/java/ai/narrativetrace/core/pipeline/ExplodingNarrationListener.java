/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import ai.narrativetrace.api.event.TraceEvent;
import java.util.function.Consumer;

/**
 * A narration listener that cannot be initialised.
 *
 * <p>INTENT: Stands in for the real {@code Slf4jTraceEventListener} in the bug hunt's repro — a
 * class present on the classpath whose static initialiser throws, which is what a shaded or
 * version-mismatched logging jar looks like from core's side. The first {@code Class.forName}
 * raises {@code ExceptionInInitializerError} and every later one {@code NoClassDefFoundError};
 * neither is an {@code Exception}, which is the whole point of the fixture.
 *
 * <p><b>@edgeCase</b> The throw is one call deep because a static initializer that cannot complete
 * normally is a compile-time error (JLS 8.7).
 */
public final class ExplodingNarrationListener implements Consumer<TraceEvent> {

  static {
    refuseToInitialise();
  }

  public ExplodingNarrationListener(String loggerName) {
    throw new IllegalStateException("unreachable: " + loggerName);
  }

  private static void refuseToInitialise() {
    throw new AssertionError("narration listener initializer");
  }

  @Override
  public void accept(TraceEvent event) {
    throw new IllegalStateException("unreachable");
  }
}
