/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.servlet;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.api.export.RequestContext;
import ai.narrativetrace.api.export.RequestContextProvider;
import ai.narrativetrace.api.export.TraceExporter;
import ai.narrativetrace.core.context.ContextExport;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.render.TraceNamer;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;

/**
 * Servlet filter that captures one trace tree per HTTP request.
 *
 * <p>INTENT: Place this near the outer edge of the servlet filter chain so downstream code traced
 * by proxies or the agent contributes to a single request-scoped trace.
 *
 * <p>Lifecycle:
 *
 * <ol>
 *   <li>Reset the narrative context (clear any stale state)
 *   <li>Adopt an inbound W3C {@code traceparent}, so a call arriving from another service continues
 *       that service's trace instead of starting a new one
 *   <li>Stamp request metadata (HTTP method, route, client IP) and user context onto the context
 *   <li>Execute the filter chain (downstream code is traced via proxies/agent)
 *   <li>Capture the accumulated trace tree
 *   <li>Export via the configured {@link TraceExporter}
 *   <li>Reset again (clean up)
 * </ol>
 *
 * <p><b>@sideEffects</b> Calls {@link NarrativeContext#reset()} before and after every HTTP
 * request. {@code reset()} is thread-scoped, so a shared singleton context safely serves concurrent
 * requests — each request thread clears only its own state.
 *
 * <p><b>@edgeCase</b> Non-HTTP servlet requests are passed through untouched. Capture, export, MDC
 * cleanup, context reset and {@link RequestContextProvider} failures are each swallowed
 * <em>independently</em>, {@link Error} subclasses included, so observability can never fail the
 * request and no cleanup step can be skipped because an earlier one failed. Cleanup runs in the
 * chain's {@code finally}, so a failure there would otherwise mask the outcome the application
 * produced.
 *
 * <p><b>@edgeCase</b> An absent, malformed or forbidden {@code traceparent} is ignored and the
 * request gets a freshly generated trace id — a stranger's bad header must never fail a request.
 *
 * @see TraceExporter
 * @see Slf4jTraceExporter
 */
public class NarrativeTraceFilter implements Filter {

  private final NarrativeContext context;
  private final TraceExporter exporter;
  private final RequestContextProvider<HttpServletRequest> provider;

  /**
   * Creates a filter with the given context and exporter.
   *
   * @param context Trace context shared with proxies or the agent. A singleton {@code
   *     ThreadLocalNarrativeContext} is the typical choice; the filter calls {@code reset()} to
   *     scope it per request.
   * @param exporter Sink that receives the captured tree after request completion.
   */
  public NarrativeTraceFilter(NarrativeContext context, TraceExporter exporter) {
    this(context, exporter, null);
  }

  /**
   * Creates a filter with the given context, exporter, and request-context provider.
   *
   * @param context Trace context shared with proxies or the agent. A singleton {@code
   *     ThreadLocalNarrativeContext} is the typical choice; the filter calls {@code reset()} to
   *     scope it per request.
   * @param exporter Sink that receives the captured tree after request completion.
   * @param provider Optional resolver for end-user, session, and tenant fields.
   */
  public NarrativeTraceFilter(
      NarrativeContext context,
      TraceExporter exporter,
      RequestContextProvider<HttpServletRequest> provider) {
    this.context = context;
    this.exporter = exporter;
    this.provider = provider;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest httpRequest)
        || !(response instanceof HttpServletResponse httpResponse)) {
      chain.doFilter(request, response);
      return;
    }
    long startTime = System.currentTimeMillis();
    prepareContext(httpRequest);
    try {
      chain.doFilter(request, response);
    } finally {
      captureAndExport(httpResponse, startTime);
    }
  }

  @SuppressWarnings("PMD.AvoidCatchingThrowable") // stamping failure must not fail the request
  private void prepareContext(HttpServletRequest httpRequest) {
    try {
      context.reset();
      stampContext(httpRequest);
    } catch (Throwable ignored) { // NOPMD
      // Observability failure must never become a request failure
    }
  }

  private void stampContext(HttpServletRequest httpRequest) {
    context.adoptTraceparent(Traceparent.parse(httpRequest.getHeader(Traceparent.HEADER_NAME)));
    var uri = httpRequest.getRequestURI();
    var addr = httpRequest.getRemoteAddr();
    var httpRoute =
        uri != null
            ? ai.narrativetrace.api.event.HttpRoute.of(ContextExport.normalized(uri))
            : null;
    var clientIp =
        addr != null
            ? ai.narrativetrace.api.event.ClientIp.of(ContextExport.normalized(addr))
            : null;
    context.setRequestContext(httpRequest.getMethod(), httpRoute, clientIp);
    applyUserContext(httpRequest);
    populateMdc(httpRequest, httpRoute, clientIp);
  }

  /**
   * Ends the request's trace: export it, then clean up, each step on its own.
   *
   * <p><b>@edgeCase</b> Three independent guards rather than one {@code try}/{@code finally}: an
   * {@code MDC.clear()} that throws used to take {@code context.reset()} down with it, leaving the
   * request thread carrying the finished request's trace stack into the next request it serves.
   */
  private void captureAndExport(HttpServletResponse httpResponse, long startTime) {
    exportTrace(httpResponse, startTime);
    clearMdc();
    resetContext();
  }

  @SuppressWarnings("PMD.AvoidCatchingThrowable") // exporter failure must not fail the request
  private void exportTrace(HttpServletResponse httpResponse, long startTime) {
    try {
      var tree = context.captureTrace();
      if (!tree.isEmpty()) {
        long duration = System.currentTimeMillis() - startTime;
        exporter.export(tree, new RequestContext(httpResponse.getStatus(), duration));
      }
    } catch (Throwable ignored) { // NOPMD
      // Observability failure must never become a request failure
    }
  }

  @SuppressWarnings("PMD.AvoidCatchingThrowable") // cleanup failure must not fail the request
  private void clearMdc() {
    try {
      MDC.clear();
    } catch (Throwable ignored) { // NOPMD
      // Observability failure must never become a request failure
    }
  }

  @SuppressWarnings("PMD.AvoidCatchingThrowable") // cleanup failure must not fail the request
  private void resetContext() {
    try {
      context.reset();
    } catch (Throwable ignored) { // NOPMD
      // Observability failure must never become a request failure
    }
  }

  /**
   * Populates the persistent MDC for this request.
   *
   * <p><b>@edgeCase</b> Every request-derived value goes through {@link ContextExport#normalized}.
   * MDC is printed by whatever layout the host configured, and a raw newline in a route, a
   * forwarded address or a method forges a log line that a line-based parser or SIEM cannot tell
   * from a real one. The method comes from the request too — a non-conforming client can send an
   * arbitrary token there.
   */
  private void populateMdc(
      HttpServletRequest httpRequest,
      ai.narrativetrace.api.event.HttpRoute httpRoute,
      ai.narrativetrace.api.event.ClientIp clientIp) {
    MDC.put("traceId", context.traceId().toString());
    MDC.put("traceName", TraceNamer.name(context.traceId().value()));
    putMdcIfPresent("httpMethod", httpRequest.getMethod());
    putMdcIfPresent("httpRoute", ContextExport.normalized(httpRoute));
    putMdcIfPresent("clientIp", ContextExport.normalized(clientIp));
  }

  private void applyUserContext(HttpServletRequest httpRequest) {
    if (provider == null) {
      return;
    }
    var userContext = provider.resolveUserContext(httpRequest);
    if (userContext == null) {
      return;
    }
    context.setUserContext(
        userContext.enduserId() != null
            ? ai.narrativetrace.api.event.EnduserId.of(userContext.enduserId())
            : null,
        userContext.sessionId() != null
            ? ai.narrativetrace.api.event.SessionId.of(userContext.sessionId())
            : null,
        userContext.tenantId() != null
            ? ai.narrativetrace.api.event.TenantId.of(userContext.tenantId())
            : null);
    putMdcIfPresent("enduserId", ContextExport.normalized(userContext.enduserId()));
    putMdcIfPresent("sessionId", ContextExport.normalized(userContext.sessionId()));
    putMdcIfPresent("tenantId", ContextExport.normalized(userContext.tenantId()));
  }

  private static void putMdcIfPresent(String key, String value) {
    if (value != null) {
      MDC.put(key, value);
    }
  }
}
