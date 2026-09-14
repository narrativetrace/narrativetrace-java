/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop;

import ai.narrativetrace.agent.AgentRuntime;
import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.soak.shop.domain.SoakMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resets the shared {@link ai.narrativetrace.core.context.NarrativeContext} per request and counts
 * every request for {@code /soak/stats}. See the notify process's identical filter for why this is
 * written directly against the public context API instead of narrativetrace-servlet/ spring-web.
 */
@Component
public class TraceContextFilter extends OncePerRequestFilter {

  private final SoakMetrics metrics;

  public TraceContextFilter(SoakMetrics metrics) {
    this.metrics = metrics;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    metrics.incrementRequest();
    var context = AgentRuntime.getContext();
    context.reset();
    context.adoptTraceparent(Traceparent.parse(request.getHeader(Traceparent.HEADER_NAME)));
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove("soak.poison");
      context.reset();
    }
  }
}
