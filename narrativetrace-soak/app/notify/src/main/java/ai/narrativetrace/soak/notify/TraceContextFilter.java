/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.notify;

import ai.narrativetrace.agent.AgentRuntime;
import ai.narrativetrace.api.event.Traceparent;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adopts the W3C {@code traceparent} header the shop process's outbound call carries, so the notify
 * process's agent-woven spans continue the shop's story instead of starting their own.
 *
 * <p>INTENT: The java agent traces methods, not HTTP requests — something still has to read the
 * inbound header and reset the shared {@link ai.narrativetrace.core.context.NarrativeContext} per
 * request. This is that something, written directly against the public context API rather than
 * pulling in narrativetrace-servlet/spring-web (see build.gradle.kts).
 */
@Component
public class TraceContextFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var context = AgentRuntime.getContext();
    context.reset();
    context.adoptTraceparent(Traceparent.parse(request.getHeader(Traceparent.HEADER_NAME)));
    try {
      chain.doFilter(request, response);
    } finally {
      context.reset();
    }
  }
}
