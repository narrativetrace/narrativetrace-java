/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.slf4j;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceOutcome;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.StringLength;

/**
 * ADR-002's safety claim extended over the LIVE emission path: whatever a runtime value contains,
 * its bytes never reach the serialized lines the {@link StructuralSubscriber} emits. The core
 * property ({@code StructuralProjectionPropertyTest}) pins projection + serialization of built
 * entries; this one starts from raw {@link TraceEvent}s, so the event-to-entry mapping is inside
 * the property too — the exact path production events travel.
 */
class StructuralSubscriberSafetyProperties {

  private static final String MARKER = "HOSTILE⚠";

  @Property
  void runtimeValuesNeverReachTheEmittedStructuralLines(
      @ForAll @StringLength(max = 40) String payload) {
    var hostile = MARKER + payload;
    var lines = new ArrayList<String>();
    var subscriber = new StructuralSubscriber(lines::add);
    var span = SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();

    subscriber.onNext(
        new TraceEvent.EnterEvent(
            span,
            System.nanoTime(),
            new MethodSignature(
                "OrderService",
                "placeOrder",
                List.of(new ParameterCapture("customerId", hostile, false)))));
    subscriber.onNext(
        new TraceEvent.ExitEvent(
            span, System.nanoTime(), new TraceOutcome.Returned(hostile), null));
    subscriber.onNext(
        new TraceEvent.ExitEvent(
            span, System.nanoTime(), new TraceOutcome.Threw(new RuntimeException(hostile)), null));

    assertThat(lines).hasSize(3);
    assertThat(String.join("\n", lines)).doesNotContain(MARKER);
  }
}
