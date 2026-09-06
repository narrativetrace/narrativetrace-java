/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("network")
@ExtendWith(NarrativeTraceExtension.class)
class NotificationServiceTest {

  @Test
  void firstCallToExternalApiSucceeds(NarrativeContext context) {
    var service =
        NarrativeTraceProxy.trace(
            new JsonPlaceholderNotificationService(), NotificationService.class, context);

    boolean sent = service.notifyOrderPlaced("C-1234", "ORD-00001").join();

    assertThat(sent).isTrue();

    var narrative = new IndentedTextRenderer().render(context.captureTrace());
    System.out.println(narrative);
    assertThat(narrative).contains("NotificationService.notifyOrderPlaced");
  }

  @Test
  void secondCallToFlakyExternalApiFails(NarrativeContext context) {
    var flaky = new FlakyNotificationService(new JsonPlaceholderNotificationService(), 2);
    var service = NarrativeTraceProxy.trace(flaky, NotificationService.class, context);

    // First call succeeds
    service.notifyOrderPlaced("C-1234", "ORD-00001");

    // Second call fails — simulates flaky external service
    assertThatThrownBy(() -> service.notifyOrderPlaced("C-1234", "ORD-00002"))
        .isInstanceOf(ExternalServiceException.class);

    var narrative = new IndentedTextRenderer().render(context.captureTrace());
    System.out.println(narrative);

    // Trace shows first call succeeded, second call threw
    assertThat(narrative).contains("Completed normally");
    assertThat(narrative).contains("ExternalServiceException");
  }

  @Test
  void httpExceptionWrapsInExternalServiceException() throws Exception {
    var service = new JsonPlaceholderNotificationService();

    // Replace HttpClient with one that throws IOException via reflection
    var clientField = JsonPlaceholderNotificationService.class.getDeclaredField("client");
    clientField.setAccessible(true);

    var failingClient =
        HttpClient.newBuilder()
            .proxy(java.net.ProxySelector.of(new java.net.InetSocketAddress("localhost", 1)))
            .connectTimeout(java.time.Duration.ofMillis(1))
            .build();
    clientField.set(service, failingClient);

    assertThatThrownBy(() -> service.notifyOrderPlaced("C-1234", "ORD-001"))
        .isInstanceOf(ExternalServiceException.class)
        .hasMessageContaining("ORD-001");
  }
}
