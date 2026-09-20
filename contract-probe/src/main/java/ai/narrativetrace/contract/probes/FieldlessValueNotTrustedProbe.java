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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * {@code probed-default}, since 0.2.4 — the rule before that granted a class with no instance field
 * its own {@code toString()}. A value carrying no field reflection can read is NOT thereby a
 * stateless leaf: this one keeps its state in a static table keyed by the instance and prints it
 * from its own {@code toString()}, which no field walk could ever see. Only a platform leaf type
 * keeps its own text now, so the sentinel must not appear anywhere in the trace.
 *
 * <p>The rule this probe measures: a traced ARGUMENT is rendered (that is what the redaction probes
 * in this package prove); this probe measures the RETURN rendering only. The sentinel must
 * therefore reach {@link Fieldless} through a channel rendering never sees — a static holder set
 * immediately before a NO-ARG traced {@code Factory.make()} call, never a traced argument.
 */
public final class FieldlessValueNotTrustedProbe {

  private FieldlessValueNotTrustedProbe() {}

  private static final String SENTINEL = "side-table-secret";

  // Set immediately before the traced no-arg call in observe(); never passed as a traced
  // argument, so the argument-capture line carries no state at all to render.
  private static final ThreadLocal<String> PENDING_HIDDEN = new ThreadLocal<>();

  /** No instance field at all; every byte of its state lives in the static table below. */
  public static final class Fieldless {
    private static final Map<Object, String> STATE =
        Collections.synchronizedMap(new IdentityHashMap<>());

    public Fieldless() {
      STATE.put(this, PENDING_HIDDEN.get());
    }

    @Override
    public String toString() {
      return "Fieldless[" + STATE.get(this) + "]";
    }
  }

  interface Factory {
    Fieldless make();
  }

  public static String observe() {
    ListAppender<ILoggingEvent> capture = SlfCapture.attach();
    Factory factory =
        NarrativeTraceProxy.trace(
            (Factory) Fieldless::new, Factory.class, new ThreadLocalNarrativeContext());
    PENDING_HIDDEN.set(SENTINEL);
    try {
      factory.make();
    } finally {
      PENDING_HIDDEN.remove();
    }
    String text = SlfCapture.allMessages(capture);
    if (text.contains(SENTINEL)) {
      return "leaked";
    }
    return text.contains("Fieldless") ? "type-named-not-tostring" : "absent";
  }
}
