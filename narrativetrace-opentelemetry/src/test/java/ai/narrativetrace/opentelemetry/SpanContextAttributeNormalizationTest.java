/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.opentelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.ClientIp;
import ai.narrativetrace.api.event.EnduserId;
import ai.narrativetrace.api.event.HttpRoute;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.core.context.ContextExport;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The last point before a context value becomes a telemetry attribute.
 *
 * <p>INTENT: The HTTP filters normalise what they read off a request, but a value set
 * programmatically through {@code setRequestContext} — by a custom integration, a test, or a
 * framework this project ships no filter for — never passed through one. An adversarial review
 * called out raw values reaching OTel attributes, where they are unbounded cardinality and, in a
 * backend that renders attributes into a log line, a forgery.
 */
@SuppressWarnings("PMD.AvoidUsingHardCodedIP") // a client IP is the subject; a literal is the point
class SpanContextAttributeNormalizationTest {

  private final InMemorySpanExporter exporter = InMemorySpanExporter.create();

  private io.opentelemetry.api.trace.Tracer tracer() {
    return SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
        .build()
        .get("test");
  }

  private io.opentelemetry.sdk.trace.data.SpanData exportWith(SpanContext sc) {
    var span = tracer().spanBuilder("s").startSpan();
    SpanContextAttributeMapper.setTraceLevelAttributes(sc, span);
    span.end();
    return exporter.getFinishedSpanItems().get(0);
  }

  private static SpanContext contextWith(String route, String ip, String user) {
    return SpanContext.builder(TraceId.generate(), SpanId.generate())
        .httpRoute(HttpRoute.of(route))
        .clientIp(ClientIp.of(ip))
        .enduserId(EnduserId.of(user))
        .build();
  }

  @Test
  @DisplayName("a raw newline set programmatically never reaches an attribute")
  void aRawNewlineNeverReachesAnAttribute() {
    var data = exportWith(contextWith("/a\nforged", "10.0.0.1\nforged", "ada\nforged"));

    data.getAttributes()
        .forEach(
            (key, value) ->
                assertThat(String.valueOf(value).chars().anyMatch(Character::isISOControl))
                    .as("attribute %s carried a raw control character", key)
                    .isFalse());
  }

  @Test
  @DisplayName("an unbounded value set programmatically is capped before export")
  void anUnboundedValueIsCapped() {
    var data = exportWith(contextWith("/" + "a".repeat(50_000), "10.0.0.1", "ada"));

    data.getAttributes()
        .forEach(
            (key, value) ->
                assertThat(String.valueOf(value).length())
                    .as("attribute %s was unbounded", key)
                    .isLessThanOrEqualTo(ContextExport.MAX_LENGTH + 1));
  }

  @Test
  @DisplayName("ordinary values are exported exactly as before")
  void ordinaryValuesAreUnchanged() {
    var data = exportWith(contextWith("/orders/{id}", "10.0.0.1", "ada"));

    assertThat(data.getAttributes().asMap())
        .containsEntry(
            io.opentelemetry.api.common.AttributeKey.stringKey("narrative.http.route"),
            "/orders/{id}")
        .containsEntry(
            io.opentelemetry.api.common.AttributeKey.stringKey("narrative.client_ip"), "10.0.0.1")
        .containsEntry(
            io.opentelemetry.api.common.AttributeKey.stringKey("narrative.enduser.id"), "ada");
  }
}
