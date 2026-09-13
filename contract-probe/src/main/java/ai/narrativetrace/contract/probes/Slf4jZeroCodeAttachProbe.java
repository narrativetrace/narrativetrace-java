/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract.probes;

import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * {@code config-shape}: narrativetrace-slf4j on the classpath must narrate through SLF4J with zero
 * application wiring — no {@code TraceEventListener} registered by hand, no logger lookup, nothing
 * but {@code new ThreadLocalNarrativeContext()} and a traced call.
 */
public final class Slf4jZeroCodeAttachProbe {

  private Slf4jZeroCodeAttachProbe() {}

  interface Greeter {
    String greet(String name);
  }

  public static String observe() {
    ListAppender<ILoggingEvent> capture = SlfCapture.attach();
    Greeter greeter =
        NarrativeTraceProxy.trace(
            (Greeter) name -> "hello " + name, Greeter.class, new ThreadLocalNarrativeContext());
    greeter.greet("world");
    return capture.list.isEmpty() ? "false" : "true";
  }
}
