/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract.probes;

import ai.narrativetrace.api.annotation.NarrativeSummary;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * {@code probed-default}, since 0.2.3 — not exercised against 0.2.1 (the fix this proves landed
 * after the tag; see documentation/privacy-and-redaction.md "When rendering a part fails"). A value
 * whose {@code @NarrativeSummary} throws must render as {@code <error: TypeName>} with the
 * exception message excluded, never the message text (a message routinely interpolates the value
 * that failed to format).
 */
public final class TypedErrorMarkerProbe {

  private TypedErrorMarkerProbe() {}

  public static final class Unrenderable {
    @NarrativeSummary
    public String summary() {
      throw new IllegalStateException("cannot render this — leaking a secret here would be a bug");
    }
  }

  interface Factory {
    Unrenderable make();
  }

  public static String observe() {
    ListAppender<ILoggingEvent> capture = SlfCapture.attach();
    Factory factory =
        NarrativeTraceProxy.trace(
            (Factory) Unrenderable::new, Factory.class, new ThreadLocalNarrativeContext());
    factory.make();
    String text = SlfCapture.allMessages(capture);
    if (text.contains("leaking a secret here would be a bug")) {
      return "message-leaked";
    }
    return text.contains("<error: IllegalStateException>")
        ? "<error: IllegalStateException>"
        : "no-marker";
  }
}
