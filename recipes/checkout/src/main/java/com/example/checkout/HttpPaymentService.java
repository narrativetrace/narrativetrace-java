/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/main/java/com/example/checkout/HttpPaymentService.java
package com.example.checkout;

import ai.narrativetrace.api.event.Traceparent;
import ai.narrativetrace.core.context.NarrativeContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * The checkout's payment collaborator when the payment service is a different process.
 *
 * <p>INTENT: The outbound half of cross-process correlation, in the shape any HTTP client copies:
 * ask the context for {@link NarrativeContext#outboundTraceparent()} and, when it is non-null, set
 * one header. The callee then continues this trace instead of starting its own.
 *
 * <p><b>@llmNote</b> The request body is a comma-separated line rather than JSON on purpose: this
 * class exists to show the header, and a parser would be the only thing a reader had to skip. A
 * real client sends whatever the payment service's API says.
 *
 * <p><b>@edgeCase</b> A null {@code outboundTraceparent()} — no span open, nothing adopted — sends
 * no header at all, which is the honest outcome: a header without a real parent id would point the
 * callee at a span that never existed.
 */
public final class HttpPaymentService implements PaymentService {

  private static final int DECLINED_STATUS = 402;

  private final NarrativeContext context;
  private final URI endpoint;
  private final HttpClient client = HttpClient.newHttpClient();

  public HttpPaymentService(NarrativeContext context, URI endpoint) {
    if (context == null || endpoint == null) {
      throw new IllegalArgumentException("context and endpoint must not be null");
    }
    this.context = context;
    this.endpoint = endpoint;
  }

  @Override
  public String charge(String customerId, int amountCents, String card) {
    var body = customerId + "," + amountCents + "," + card;
    var request =
        withTraceparent(
            HttpRequest.newBuilder()
                .uri(endpoint)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
    var response = send(request.build());
    if (response.statusCode() == DECLINED_STATUS) {
      throw new PaymentDeclinedException(response.body());
    }
    return response.body();
  }

  /** Sets the W3C header when this call has a span to name as the callee's parent. */
  private HttpRequest.Builder withTraceparent(HttpRequest.Builder builder) {
    var traceparent = context.outboundTraceparent();
    if (traceparent == null) {
      return builder;
    }
    return builder.header(Traceparent.HEADER_NAME, traceparent.format());
  }

  private HttpResponse<String> send(HttpRequest request) {
    try {
      return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException("the payment service could not be reached", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while charging", e);
    }
  }
}
