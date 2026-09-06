/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.NoopNarrativeContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Traced service that calls a real external API — and, because it is traced, sends the W3C {@code
 * traceparent} header so the callee can continue this story instead of starting its own.
 *
 * <p>INTENT: This is the outbound half of cross-process correlation, in the shape any HTTP client
 * copies: ask the context for {@link NarrativeContext#outboundTraceparent()} and, when it is
 * non-null, set one header. The inbound half is the servlet or Micronaut filter.
 */
public class JsonPlaceholderNotificationService implements NotificationService {

  private final HttpClient client = HttpClient.newHttpClient();
  private final NarrativeContext context;

  /** Untraced use — no context means no header, which is the honest outcome. */
  public JsonPlaceholderNotificationService() {
    this(NoopNarrativeContext.INSTANCE);
  }

  public JsonPlaceholderNotificationService(NarrativeContext context) {
    this.context = context;
  }

  @Override
  public CompletableFuture<Boolean> notifyOrderPlaced(String customerId, String orderId) {
    try {
      var body =
          """
                    {"customerId": "%s", "orderId": "%s", "type": "ORDER_CONFIRMATION"}"""
              .formatted(customerId, orderId);

      var builder =
          HttpRequest.newBuilder()
              .uri(URI.create("https://jsonplaceholder.typicode.com/posts"))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body));
      var request = withTraceparent(builder).build();

      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return CompletableFuture.completedFuture(response.statusCode() == 201);
    } catch (Exception e) {
      throw new ExternalServiceException("Failed to send notification for order " + orderId, e);
    }
  }

  /** Package-private so the header decision is testable without a network round trip. */
  HttpRequest.Builder withTraceparent(HttpRequest.Builder builder) {
    var traceparent = context.outboundTraceparent();
    if (traceparent == null) {
      return builder;
    }
    return builder.header(Traceparent.HEADER_NAME, traceparent.format());
  }
}
