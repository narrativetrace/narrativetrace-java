/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The outbound half of cross-process correlation, asserted without leaving the JVM. */
class OutboundTraceparentTest {

  private final ThreadLocalNarrativeContext context = new ThreadLocalNarrativeContext();

  @AfterEach
  void tearDown() {
    context.reset();
  }

  @Test
  void aTracedCallSendsTheHeaderNamingItsOwnSpan() {
    var spanId =
        context.enterMethod(new MethodSignature("NotificationService", "notify", List.of()));
    var service = new JsonPlaceholderNotificationService(context);

    var request = service.withTraceparent(newBuilder()).build();

    assertThat(request.headers().firstValue(Traceparent.HEADER_NAME))
        .hasValue(new Traceparent(context.traceId(), spanId, 1).format());
    context.exitMethodWithReturn("\"sent\"", spanId);
  }

  @Test
  void anUntracedCallSendsNoHeaderAtAll() {
    var service = new JsonPlaceholderNotificationService();

    var request = service.withTraceparent(newBuilder()).build();

    assertThat(request.headers().firstValue(Traceparent.HEADER_NAME)).isEmpty();
  }

  @Test
  void aTracedCallOutsideAnyMethodSendsNoHeader() {
    var service = new JsonPlaceholderNotificationService(context);

    var request = service.withTraceparent(newBuilder()).build();

    assertThat(request.headers().firstValue(Traceparent.HEADER_NAME))
        .as("no open span means no honest parent id to name")
        .isEmpty();
  }

  /**
   * The capability this example only inspects elsewhere: the header leaves the JVM and the callee
   * actually receives it, over a real loopback socket rather than a formatted {@link HttpRequest}.
   */
  @Test
  void theHeaderReachesTheServerThatReceivesTheRequest() throws Exception {
    var spanId =
        context.enterMethod(new MethodSignature("NotificationService", "notify", List.of()));
    var service = new JsonPlaceholderNotificationService(context);
    var receivedHeader = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          receivedHeader.set(exchange.getRequestHeaders().getFirst(Traceparent.HEADER_NAME));
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();
    try {
      var uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/");
      var request = service.withTraceparent(HttpRequest.newBuilder().uri(uri).GET()).build();
      HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
    } finally {
      server.stop(0);
    }
    context.exitMethodWithReturn("\"sent\"", spanId);

    assertThat(receivedHeader.get())
        .isEqualTo(new Traceparent(context.traceId(), spanId, 1).format());
  }

  private static HttpRequest.Builder newBuilder() {
    return HttpRequest.newBuilder().uri(URI.create("https://example.invalid/posts")).GET();
  }
}
