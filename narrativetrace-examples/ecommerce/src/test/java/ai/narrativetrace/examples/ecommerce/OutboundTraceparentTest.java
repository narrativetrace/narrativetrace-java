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
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;
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

  private static HttpRequest.Builder newBuilder() {
    return HttpRequest.newBuilder().uri(URI.create("https://example.invalid/posts")).GET();
  }
}
