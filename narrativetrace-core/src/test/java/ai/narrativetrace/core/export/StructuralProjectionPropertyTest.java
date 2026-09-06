/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.StringLength;

/**
 * ADR-002's safety claim as a testable property: no runtime value ever reaches serialized Level-1
 * structural output, whatever the value contains. Every runtime-value channel (rendered message,
 * parameter values, return value, exception message) is seeded with marked hostile content; the
 * marker must never survive projection + serialization.
 */
class StructuralProjectionPropertyTest {

  private static final String MARKER = "HOSTILE⚠";

  @Property
  void runtimeValuesNeverReachSerializedStructuralOutput(
      @ForAll @StringLength(max = 40) String payload) {
    var hostile = MARKER + payload;

    var enter = enterEntryWithValues(hostile);
    var exit = exitEntryWithValues(hostile);

    var enterJson = CanonicalEntrySerializer.toJson(StructuralProjection.project(enter));
    var exitJson = CanonicalEntrySerializer.toJson(StructuralProjection.project(exit));

    assertThat(enterJson).doesNotContain(MARKER);
    assertThat(exitJson).doesNotContain(MARKER);
  }

  private static CanonicalEntry enterEntryWithValues(String hostile) {
    return CanonicalEntry.builder()
        .timestamp("2026-03-19T10:23:01.123Z")
        .level("trace")
        .message("→ OrderService.placeOrder(customerId: " + hostile + ")")
        .service("order-service")
        .environment("production")
        .traceId("0123456789abcdef0123456789abcdef")
        .spanId("0123456789abcdef")
        .codeNamespace("OrderService")
        .codeFunction("placeOrder")
        .ntEntryType("entry")
        .ntEventType("method_enter")
        .ntSchemaVersion("1.1")
        .ntTraceName("bold elk soars")
        .ntStoryId("OrderService.placeOrder")
        .ntChapterId("OrderService.placeOrder")
        .ntParameters(List.of(new ParameterEntry("customerId", hostile, false)))
        .ntNarrationTemplate("Placing order for {customerId}")
        .build();
  }

  private static CanonicalEntry exitEntryWithValues(String hostile) {
    return CanonicalEntry.builder()
        .timestamp("2026-03-19T10:23:01.456Z")
        .level("error")
        .message("!! IllegalStateException: " + hostile)
        .service("order-service")
        .environment("production")
        .traceId("0123456789abcdef0123456789abcdef")
        .spanId("0123456789abcdef")
        .codeNamespace("OrderService")
        .codeFunction("placeOrder")
        .ntEntryType("entry")
        .ntEventType("method_exit")
        .ntSchemaVersion("1.1")
        .ntTraceName("bold elk soars")
        .ntStoryId("OrderService.placeOrder")
        .ntChapterId("OrderService.placeOrder")
        .ntOutcome("failure")
        .durationMs(333L)
        .ntReturnValue(hostile)
        .exceptionType("IllegalStateException")
        .exceptionMessage(hostile)
        .build();
  }
}
