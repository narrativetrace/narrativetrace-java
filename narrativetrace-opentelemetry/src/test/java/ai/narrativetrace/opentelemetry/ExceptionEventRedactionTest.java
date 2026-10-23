/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.opentelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.render.RedactionPolicy;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExceptionEventRedactionTest {

  private final InMemorySpanExporter output = InMemorySpanExporter.create();
  private final SdkTracerProvider provider =
      SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(output)).build();

  @AfterEach
  void closeProvider() {
    provider.close();
  }

  @Test
  void aCausesMessageIsRedactedInTheStacktraceToo() {
    // Public test PAN, not a real credential. The policy explicitly recognizes these bytes.
    var sensitive = "4111111111111111";
    var failure = new IllegalStateException("outer", new IllegalArgumentException(sensitive));

    var event = exportAndTakeExceptionEvent(failure);

    var stacktrace = event.getAttributes().get(AttributeKey.stringKey("exception.stacktrace"));
    assertThat(stacktrace).doesNotContain(sensitive).contains("IllegalArgumentException");
    assertThat(stacktrace).contains(RedactionPolicy.MARKER);
  }

  @Test
  void theEventCarriesTypeRedactedMessageAndFramesWithoutTheOuterMessage() {
    var sensitive = "4111111111111111";
    var failure = new IllegalStateException(sensitive);

    var event = exportAndTakeExceptionEvent(failure);

    assertThat(event.getName()).isEqualTo("exception");
    assertThat(event.getAttributes().get(AttributeKey.stringKey("exception.type")))
        .isEqualTo("java.lang.IllegalStateException");
    assertThat(event.getAttributes().get(AttributeKey.stringKey("exception.message")))
        .isEqualTo(RedactionPolicy.MARKER);
    assertThat(event.getAttributes().get(AttributeKey.stringKey("exception.stacktrace")))
        .startsWith("java.lang.IllegalStateException\n\tat ")
        .doesNotContain(sensitive);
  }

  private io.opentelemetry.sdk.trace.data.EventData exportAndTakeExceptionEvent(Throwable failure) {
    var node =
        new TraceNode(
            new MethodSignature("Payment", "charge", List.of()),
            List.of(),
            new TraceOutcome.Threw(failure));
    new TraceSpanExporter(provider.get("test")).export(List.of(node));
    return output.getFinishedSpanItems().get(0).getEvents().get(0);
  }
}
