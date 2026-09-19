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
 * {@code probed-default}, since 0.2.4 — the rule before that read a {@code Number}'s own {@code
 * toString()} on the flat path once the text was escaped and capped. Extending {@code Number} says
 * nothing about what a value holds: this one carries a deny-listed field and prints it, so only a
 * walk hides it. The sentinel must appear nowhere in the trace and the marker must stand in its
 * place, exactly as it does for any other composite.
 */
public final class NumberSubclassNotTrustedProbe {

  private NumberSubclassNotTrustedProbe() {}

  /** A money type an application author would plausibly write, and its curated numeric text. */
  public static final class Amount extends Number {

    private static final long serialVersionUID = 1L;

    private final long cents;
    private final String password;

    public Amount(long cents, String password) {
      this.cents = cents;
      this.password = password;
    }

    // Curated, hand-written — deliberately the leak this probe proves is closed.
    @Override
    public String toString() {
      return "Amount{cents=" + cents + ", password=" + password + "}";
    }

    @Override
    public int intValue() {
      return (int) cents;
    }

    @Override
    public long longValue() {
      return cents;
    }

    @Override
    public float floatValue() {
      return cents;
    }

    @Override
    public double doubleValue() {
      return cents;
    }
  }

  interface Factory {
    Amount make(long cents, String password);
  }

  public static String observe() {
    ListAppender<ILoggingEvent> capture = SlfCapture.attach();
    Factory factory =
        NarrativeTraceProxy.trace(
            (Factory) Amount::new, Factory.class, new ThreadLocalNarrativeContext());
    factory.make(1999L, "hunter2");
    String text = SlfCapture.allMessages(capture);
    if (text.contains("hunter2")) {
      return "leaked";
    }
    return text.contains("[REDACTED]") ? "field-walked-not-tostring" : "no-marker";
  }
}
