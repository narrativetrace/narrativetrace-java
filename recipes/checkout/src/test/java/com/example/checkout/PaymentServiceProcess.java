/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/test/java/com/example/checkout/PaymentServiceProcess.java
package com.example.checkout;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.PipelineBootstrap;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The payment service, as a second traced service: a loopback HTTP server with its OWN narrative
 * context, narrating under its own logger name.
 *
 * <p>INTENT: The inbound half of cross-process correlation, for
 * documentation/recipes/follow-one-request-across-services.md. The handler does what a servlet
 * filter ({@code narrativetrace-servlet}'s {@code NarrativeTraceFilter}) does for a framework
 * application, by hand, because this project runs without one: reset, adopt the inbound {@code
 * traceparent}, serve the request, capture, reset.
 *
 * <p><b>@llmNote</b> Two services in one JVM, not a mock: the request really crosses a socket and
 * the header really arrives as a header. What a second process would add is a second classpath,
 * which is exactly what this page does not need to prove. The second context is what makes it a
 * second service — no span of its own is visible to the caller's {@code captureTrace()}, and its
 * lines carry its own logger name.
 *
 * <p><b>@sideEffects</b> One daemon thread per instance, shut down by {@link #close()}; the handler
 * resets its context before and after every request, like the filter it stands in for.
 */
final class PaymentServiceProcess implements AutoCloseable {

  /** The callee's logger name, so one log file tells the two services' lines apart. */
  static final String LOGGER_NAME = "narrativetrace.payments";

  private static final int OK = 200;
  private static final int PAYMENT_REQUIRED = 402;

  // snippet:begin own-context
  private final NarrativeContext context =
      new ThreadLocalNarrativeContext(
          new NarrativeTraceConfig(), PipelineBootstrap.createDefault(LOGGER_NAME));
  private final PaymentService payments =
      NarrativeTraceProxy.trace(new CardPaymentService(), PaymentService.class, context);
  // snippet:end own-context
  private final AtomicReference<String> inboundHeader = new AtomicReference<>();
  private final AtomicReference<TraceTree> lastTrace = new AtomicReference<>();
  private final ExecutorService requests =
      Executors.newSingleThreadExecutor(PaymentServiceProcess::serviceThread);
  private final HttpServer server;

  PaymentServiceProcess() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/charge", this::handle);
    server.setExecutor(requests);
    server.start();
  }

  /** Where the caller sends its charge — a loopback port the OS picked. */
  URI endpoint() {
    return URI.create("http://localhost:" + server.getAddress().getPort() + "/charge");
  }

  /** The raw {@code traceparent} the last request carried, or null when it carried none. */
  String inboundTraceparent() {
    return inboundHeader.get();
  }

  /** The trace this service captured for the last request — its own narrative, not the caller's. */
  TraceTree lastTrace() {
    return lastTrace.get();
  }

  // snippet:begin inbound
  private void handle(HttpExchange exchange) throws IOException {
    var header = exchange.getRequestHeaders().getFirst(Traceparent.HEADER_NAME);
    inboundHeader.set(header);
    context.reset();
    context.adoptTraceparent(Traceparent.parse(header));
    try {
      var fields = body(exchange).split(",", 3);
      respond(exchange, OK, payments.charge(fields[0], Integer.parseInt(fields[1]), fields[2]));
    } catch (PaymentDeclinedException declined) {
      respond(exchange, PAYMENT_REQUIRED, declined.getMessage());
    } finally {
      lastTrace.set(context.captureTrace());
      context.reset();
    }
  }

  // snippet:end inbound

  private static String body(HttpExchange exchange) throws IOException {
    return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    var bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    try (var out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  /** A named daemon thread, so the page's embedded output names the same thread every run. */
  private static Thread serviceThread(Runnable runnable) {
    var thread = new Thread(runnable, "payment-service-1");
    thread.setDaemon(true);
    return thread;
  }

  @Override
  public void close() {
    server.stop(0);
    requests.shutdownNow();
  }
}
