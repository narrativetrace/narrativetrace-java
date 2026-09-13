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
import java.util.UUID;

/**
 * {@code probed-default}, since 0.2.2 — not exercised against 0.2.1. A JDK-defined type ({@link
 * UUID} here) declares no field of application code's choosing and cannot carry a
 * {@code @NotTraced} member, so it keeps its own {@code toString()} rather than being walked.
 */
public final class PlatformTypeCarveoutProbe {

  private PlatformTypeCarveoutProbe() {}

  private static final UUID FIXED_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

  interface Factory {
    UUID make();
  }

  public static String observe() {
    ListAppender<ILoggingEvent> capture = SlfCapture.attach();
    Factory factory =
        NarrativeTraceProxy.trace(
            (Factory) () -> FIXED_ID, Factory.class, new ThreadLocalNarrativeContext());
    factory.make();
    String text = SlfCapture.allMessages(capture);
    return text.contains(FIXED_ID.toString()) ? "own-tostring-used" : "field-walked";
  }
}
