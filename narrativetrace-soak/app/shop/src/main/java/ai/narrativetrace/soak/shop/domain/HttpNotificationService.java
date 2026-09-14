/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.domain;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.examples.ecommerce.NotificationService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Posts the order-confirmation notification to the soak's own downstream notify process — replacing
 * the ecommerce example's real call to a third-party API so the soak never reaches an external
 * host, following the same outbound-{@code traceparent} pattern {@code
 * JsonPlaceholderNotificationService} documents.
 *
 * <p><b>@sideEffects</b> Runs on {@code executor}, not the calling (request) thread — a slow or
 * failing downstream must never hold up the order response. Failures are logged and reported as
 * {@code false}, never rethrown: this is fire-and-forget, matching how the example's own
 * {@code @Async} contract is meant to behave.
 */
public class HttpNotificationService implements NotificationService {

  private static final Logger log = LoggerFactory.getLogger(HttpNotificationService.class);

  private final HttpClient client = HttpClient.newHttpClient();
  private final NarrativeContext context;
  private final String notifyBaseUrl;
  private final Executor executor;

  public HttpNotificationService(
      NarrativeContext context, String notifyBaseUrl, Executor executor) {
    this.context = context;
    this.notifyBaseUrl = notifyBaseUrl;
    this.executor = executor;
  }

  @Override
  public CompletableFuture<Boolean> notifyOrderPlaced(String customerId, String orderId) {
    return CompletableFuture.supplyAsync(() -> sendNotification(customerId, orderId), executor);
  }

  private boolean sendNotification(String customerId, String orderId) {
    try {
      var request = withTraceparent(newRequest(customerId, orderId)).build();
      var response = client.send(request, HttpResponse.BodyHandlers.discarding());
      return response.statusCode() == 200;
    } catch (Exception e) {
      log.warn("Failed to notify for order {}: {}", orderId, e.toString());
      return false;
    }
  }

  private HttpRequest.Builder newRequest(String customerId, String orderId) {
    var body =
        """
        {"customerId": "%s", "orderId": "%s", "type": "ORDER_CONFIRMATION"}\
        """
            .formatted(customerId, orderId);
    return HttpRequest.newBuilder()
        .uri(URI.create(notifyBaseUrl + "/notifications"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body));
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
