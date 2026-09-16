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
 * {@code probed-default}, since 0.2.3 — not exercised against 0.2.1 (this repo's rule ran the
 * opposite way before 2026-09-11, documentation/privacy-and-redaction.md). A class declaring
 * instance fields must be walked field-by-field, its own curated {@code toString()} ignored, so a
 * hand-written {@code toString()} that interpolates {@code password} can never leak it at depth
 * zero. Returns a sentinel (not the exact rendered literal, which this repo's formatting is free to
 * evolve) so this probe stays about the invariant, not about incidental punctuation.
 */
public final class NativeStringificationNotTrustedProbe {

  private NativeStringificationNotTrustedProbe() {}

  public static final class Login {
    private final String username;
    private final String password;

    public Login(String username, String password) {
      this.username = username;
      this.password = password;
    }

    public String getUsername() {
      return username;
    }

    public String getPassword() {
      return password;
    }

    // Curated, hand-written — deliberately the leak this probe proves is closed.
    @Override
    public String toString() {
      return "Login{username=" + username + ", password=" + password + "}";
    }
  }

  interface Factory {
    Login make(String username, String password);
  }

  public static String observe() {
    ListAppender<ILoggingEvent> capture = SlfCapture.attach();
    Factory factory =
        NarrativeTraceProxy.trace(
            (Factory) Login::new, Factory.class, new ThreadLocalNarrativeContext());
    factory.make("alice", "hunter2");
    String text = SlfCapture.allMessages(capture);
    if (text.contains("hunter2")) {
      return "leaked";
    }
    return text.contains("[REDACTED]") ? "field-walked-not-tostring" : "no-marker";
  }
}
