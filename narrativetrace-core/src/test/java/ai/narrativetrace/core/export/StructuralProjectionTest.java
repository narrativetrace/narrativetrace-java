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
import org.junit.jupiter.api.Test;

class StructuralProjectionTest {

  @Test
  void elidesParameterValuesKeepingNamesAndRedactionFlags() {
    var entry =
        enterEntry(
            List.of(
                new ParameterEntry("customerId", "C-123", false),
                new ParameterEntry("password", "[REDACTED]", true)));

    var projected = StructuralProjection.project(entry);

    assertThat(projected.ntParameters())
        .containsExactly(
            new ParameterEntry("customerId", "[ELIDED]", false),
            new ParameterEntry("password", "[ELIDED]", true));
  }

  @Test
  void rebuildsEnterMessageFromParameterNamesOnly() {
    var entry =
        enterEntry(
            List.of(
                new ParameterEntry("customerId", "C-123", false),
                new ParameterEntry("password", "[REDACTED]", true)));

    var projected = StructuralProjection.project(entry);

    assertThat(projected.message()).isEqualTo("→ OrderService.placeOrder(customerId, password)");
  }

  @Test
  void rebuildsEnterMessageWithoutParenthesesContentWhenNoParameters() {
    var entry = enterEntry(null);

    var projected = StructuralProjection.project(entry);

    assertThat(projected.message()).isEqualTo("→ OrderService.placeOrder()");
  }

  @Test
  void rebuildsExitMessagesWithoutRuntimeValues() {
    var returned =
        exitEntry(
            "← OrderService.placeOrder returned OrderResult[id=7]",
            "OrderResult[id=7]",
            null,
            null);
    var threw =
        exitEntry(
            "!! IllegalStateException: card 4111-1111 declined",
            null,
            "IllegalStateException",
            "card 4111-1111 declined");

    assertThat(StructuralProjection.project(returned).message())
        .isEqualTo("← OrderService.placeOrder returned");
    assertThat(StructuralProjection.project(threw).message()).isEqualTo("!! IllegalStateException");
  }

  @Test
  void rebuildsIncompleteExitMessageFromCodeIdentity() {
    var incomplete =
        exitEntry("← OrderService.placeOrder incomplete", null, null, null, "incomplete");

    assertThat(StructuralProjection.project(incomplete).message())
        .isEqualTo("← OrderService.placeOrder incomplete");
  }

  @Test
  void elidesReturnValueAndExceptionMessageKeepingExceptionType() {
    var entry =
        exitEntry("← placeOrder returned OrderResult[id=7]", "OrderResult[id=7]", null, null);
    var threw =
        exitEntry(
            "!! IllegalStateException: card 4111-1111 declined",
            null,
            "IllegalStateException",
            "card 4111-1111 declined");

    var projected = StructuralProjection.project(entry);
    var projectedThrew = StructuralProjection.project(threw);

    assertThat(projected.ntReturnValue()).isNull();
    assertThat(projectedThrew.exceptionMessage()).isNull();
    assertThat(projectedThrew.exceptionType()).isEqualTo("IllegalStateException");
  }

  @Test
  void preservesStructureFieldsAndNarrationTemplate() {
    var entry = enterEntry(List.of(new ParameterEntry("customerId", "C-123", false)));

    var projected = StructuralProjection.project(entry);

    assertThat(projected.ntNarrationTemplate()).isEqualTo("Placing order for {customerId}");
    assertThat(projected.timestamp()).isEqualTo(entry.timestamp());
    assertThat(projected.traceId()).isEqualTo(entry.traceId());
    assertThat(projected.spanId()).isEqualTo(entry.spanId());
    assertThat(projected.codeNamespace()).isEqualTo(entry.codeNamespace());
    assertThat(projected.codeFunction()).isEqualTo(entry.codeFunction());
    assertThat(projected.ntEventType()).isEqualTo(entry.ntEventType());
    assertThat(projected.ntSchemaVersion()).isEqualTo(entry.ntSchemaVersion());
    assertThat(projected.ntTraceName()).isEqualTo(entry.ntTraceName());
  }

  @Test
  void preservesOutcomeAndDurationOnExitEntries() {
    var entry =
        exitEntry("← placeOrder returned OrderResult[id=7]", "OrderResult[id=7]", null, null);

    var projected = StructuralProjection.project(entry);

    assertThat(projected.ntOutcome()).isEqualTo("success");
    assertThat(projected.durationMs()).isEqualTo(333L);
    assertThat(projected.level()).isEqualTo("trace");
  }

  @Test
  void passesConcurrencyEntriesThroughUnchanged() {
    var fork =
        CanonicalEntry.builder()
            .timestamp("2026-03-19T10:23:01.200Z")
            .level("trace")
            .message("fork [fork-1]")
            .ntEntryType("entry")
            .ntEventType("fork")
            .ntSchemaVersion("1.1")
            .ntForkId("fork-1")
            .build();

    assertThat(StructuralProjection.project(fork)).isEqualTo(fork);
  }

  @Test
  void rejectsNullEntry() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> StructuralProjection.project(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("entry");
  }

  private static CanonicalEntry exitEntry(
      String message, String returnValue, String exceptionType, String exceptionMessage) {
    return exitEntry(
        message,
        returnValue,
        exceptionType,
        exceptionMessage,
        exceptionType == null ? "success" : "failure");
  }

  private static CanonicalEntry exitEntry(
      String message,
      String returnValue,
      String exceptionType,
      String exceptionMessage,
      String outcome) {
    return CanonicalEntry.builder()
        .timestamp("2026-03-19T10:23:01.456Z")
        .level(exceptionType == null ? "trace" : "error")
        .message(message)
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
        .ntOutcome(outcome)
        .durationMs(333L)
        .ntReturnValue(returnValue)
        .exceptionType(exceptionType)
        .exceptionMessage(exceptionMessage)
        .build();
  }

  private static CanonicalEntry enterEntry(List<ParameterEntry> parameters) {
    return CanonicalEntry.builder()
        .timestamp("2026-03-19T10:23:01.123Z")
        .level("trace")
        .message("→ OrderService.placeOrder(customerId: C-123, password: [REDACTED])")
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
        .ntParameters(parameters)
        .ntNarrationTemplate("Placing order for {customerId}")
        .build();
  }
}
