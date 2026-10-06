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
 * {@code probed-default}: a parameter literally named {@code password} is redacted at capture — no
 * {@code @NotTraced}, no name-shape heuristic beyond the deny-list itself.
 */
public final class ParameterNameRedactionProbe {

  private ParameterNameRedactionProbe() {}

  interface LoginService {
    String login(String username, String password);
  }

  public static String observe() {
    ListAppender<ILoggingEvent> capture = SlfCapture.attach();
    LoginService service =
        NarrativeTraceProxy.trace(
            (LoginService) (username, password) -> "ok",
            LoginService.class,
            new ThreadLocalNarrativeContext());
    service.login("alice", "hunter2");
    String text = SlfCapture.allMessages(capture);
    if (text.contains("hunter2")) {
      return "leaked";
    }
    return text.contains("[REDACTED]") ? "[REDACTED]" : "no-marker";
  }
}
