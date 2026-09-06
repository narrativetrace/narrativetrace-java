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

/** {@link CanonicalEntry.Builder} mirrors the canonical constructor field-for-field. */
class CanonicalEntryBuilderTest {

  @Test
  void builderWithEveryFieldSetEqualsPositionalConstruction() {
    var parameters = List.of(new ParameterEntry("customerId", "C1", false));
    var positional =
        new CanonicalEntry(
            "2026-01-15T10:30:00.000Z",
            "trace",
            "→ OrderService.placeOrder(customerId: C1)",
            "order-service",
            "test",
            "0af7651916cd43dd8448eb211c80319c",
            "b7ad6b7169203331",
            "a1b2c3d4e5f60718",
            "OrderService",
            "placeOrder",
            "entry",
            "method_enter",
            "1.1",
            "bold elk soars",
            "story-1",
            "chapter-1",
            "success",
            "fork-1",
            2,
            "causal-1",
            42L,
            parameters,
            "\"ok\"",
            "PaymentDeclinedException",
            "Card expired",
            "Placing order for {customerId}",
            "com.acme.billing",
            "com.acme.payments",
            "java.lang.String",
            "worker-3",
            7L,
            false,
            "web-1",
            4242L,
            "17.0.10+7",
            "1a2b3c4d",
            "OrderService.java",
            42);

    var built =
        CanonicalEntry.builder()
            .timestamp("2026-01-15T10:30:00.000Z")
            .level("trace")
            .message("→ OrderService.placeOrder(customerId: C1)")
            .service("order-service")
            .environment("test")
            .traceId("0af7651916cd43dd8448eb211c80319c")
            .spanId("b7ad6b7169203331")
            .parentSpanId("a1b2c3d4e5f60718")
            .codeNamespace("OrderService")
            .codeFunction("placeOrder")
            .ntEntryType("entry")
            .ntEventType("method_enter")
            .ntSchemaVersion("1.1")
            .ntTraceName("bold elk soars")
            .ntStoryId("story-1")
            .ntChapterId("chapter-1")
            .ntOutcome("success")
            .ntForkId("fork-1")
            .ntBranchIndex(2)
            .ntCausalId("causal-1")
            .durationMs(42L)
            .ntParameters(parameters)
            .ntReturnValue("\"ok\"")
            .exceptionType("PaymentDeclinedException")
            .exceptionMessage("Card expired")
            .ntNarrationTemplate("Placing order for {customerId}")
            .ntPackage("com.acme.billing")
            .ntExceptionPackage("com.acme.payments")
            .ntReturnType("java.lang.String")
            .threadName("worker-3")
            .threadId(7L)
            .ntThreadVirtual(false)
            .hostName("web-1")
            .processPid(4242L)
            .runtimeVersion("17.0.10+7")
            .ntInstanceId("1a2b3c4d")
            .codeFilepath("OrderService.java")
            .codeLineno(42)
            .build();

    assertThat(built).isEqualTo(positional);
  }

  @Test
  void unsetOptionalFieldsDefaultToNull() {
    var built =
        CanonicalEntry.builder()
            .timestamp("2026-01-15T10:30:00.000Z")
            .level("trace")
            .message("fork [f-1]")
            .ntEntryType("entry")
            .ntEventType("fork")
            .ntSchemaVersion("1.1")
            .build();

    // Every unset field must be null — enumerated reflectively so newly added record components
    // are covered automatically instead of needing one assertion per field.
    var setComponents =
        java.util.Set.of(
            "timestamp", "level", "message", "ntEntryType", "ntEventType", "ntSchemaVersion");
    for (var component : CanonicalEntry.class.getRecordComponents()) {
      if (setComponents.contains(component.getName())) {
        continue;
      }
      try {
        assertThat(component.getAccessor().invoke(built))
            .as("unset component %s must default to null", component.getName())
            .isNull();
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }
  }

  @Test
  void toBuilderRoundTripsEveryField() {
    var original =
        new CanonicalEntry(
            "2026-01-15T10:30:00.000Z",
            "error",
            "!! IllegalStateException: boom",
            "svc",
            "production",
            "0af7651916cd43dd8448eb211c80319c",
            "b7ad6b7169203331",
            null,
            "OrderService",
            "placeOrder",
            "entry",
            "method_exit",
            "1.1",
            "bold elk soars",
            "story-1",
            "chapter-1",
            "failure",
            null,
            null,
            null,
            7L,
            List.of(new ParameterEntry("secret", "[REDACTED]", true)),
            null,
            "IllegalStateException",
            "boom",
            null,
            "com.acme.billing",
            "java.lang",
            "void",
            "main",
            1L,
            true,
            "web-1",
            99L,
            "21.0.2+13",
            "deadbeef",
            "Svc.java",
            7);

    assertThat(original.toBuilder().build()).isEqualTo(original);
  }

  @Test
  void toBuilderSupportsSelectiveOverride() {
    var original =
        CanonicalEntry.builder()
            .timestamp("2026-01-15T10:30:00.000Z")
            .level("trace")
            .message("→ OrderService.placeOrder()")
            .ntEntryType("entry")
            .ntEventType("method_enter")
            .ntSchemaVersion("1.1")
            .ntReturnValue("\"ok\"")
            .build();

    var overridden = original.toBuilder().ntReturnValue(null).build();

    assertThat(overridden.ntReturnValue()).isNull();
    assertThat(overridden.message()).isEqualTo(original.message());
    assertThat(overridden.timestamp()).isEqualTo(original.timestamp());
  }
}
