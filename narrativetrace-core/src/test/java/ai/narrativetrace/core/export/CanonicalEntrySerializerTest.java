/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalEntrySerializerTest {

  @Test
  void controlCharactersInFieldsProduceParseableJson() throws Exception {
    var hostileMessage = "bad" + (char) 1 + "\b\f message \"quoted\"";
    var entry =
        CanonicalEntry.builder()
            .timestamp("2026-03-19T10:23:01.123Z")
            .level("trace")
            .message(hostileMessage)
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
            .build();

    var json = CanonicalEntrySerializer.toJson(entry);

    var parsed = new ObjectMapper().readTree(json);
    assertThat(parsed.at("/message").asText()).isEqualTo(hostileMessage);
  }

  @Test
  void serializesRequiredFieldsWithDottedNames() {
    var entry =
        CanonicalEntry.builder()
            .timestamp("2026-03-19T10:23:01.123Z")
            .level("trace")
            .message("\u2192 OrderService.placeOrder()")
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
            .build();

    var json = CanonicalEntrySerializer.toJson(entry);

    assertThat(json).contains("\"code.namespace\": \"OrderService\"");
    assertThat(json).contains("\"code.function\": \"placeOrder\"");
    assertThat(json).contains("\"nt.entryType\": \"entry\"");
    assertThat(json).contains("\"nt.eventType\": \"method_enter\"");
    assertThat(json).contains("\"nt.schemaVersion\": \"1.1\"");
    assertThat(json).contains("\"nt.traceName\": \"bold elk soars\"");
    assertThat(json).contains("\"nt.storyId\": \"OrderService.placeOrder\"");
    assertThat(json).contains("\"trace_id\": \"0123456789abcdef0123456789abcdef\"");
    assertThat(json).contains("\"span_id\": \"0123456789abcdef\"");
    assertThat(json).doesNotContain("\"parent_span_id\"");
  }

  @Test
  void writesPackageFieldsWhenPresentAndOmitsThemWhenNull() {
    var withPackages =
        CanonicalEntry.builder()
            .timestamp("2026-03-19T10:23:01.123Z")
            .level("error")
            .service("order-service")
            .message("!! PaymentDeclinedException: Card expired")
            .ntEntryType("entry")
            .ntEventType("method_exit")
            .ntSchemaVersion("1.2")
            .ntPackage("com.acme.billing")
            .ntExceptionPackage("com.acme.payments")
            .build();
    var withoutPackages = withPackages.toBuilder().ntPackage(null).ntExceptionPackage(null).build();

    assertThat(CanonicalEntrySerializer.toJson(withPackages))
        .contains("\"nt.package\": \"com.acme.billing\"")
        .contains("\"nt.exceptionPackage\": \"com.acme.payments\"");
    assertThat(CanonicalEntrySerializer.toJson(withoutPackages))
        .doesNotContain("\"nt.package\"")
        .doesNotContain("\"nt.exceptionPackage\"");
  }

  @Test
  void writesTypeFieldsWhenPresentAndOmitsThemWhenNull() {
    var withTypes =
        CanonicalEntry.builder()
            .timestamp("2026-03-19T10:23:01.123Z")
            .level("trace")
            .service("order-service")
            .message("→ OrderService.find(id: \"X\")")
            .ntEntryType("entry")
            .ntEventType("method_enter")
            .ntSchemaVersion("1.2")
            .ntReturnType("java.lang.String")
            .ntParameters(List.of(new ParameterEntry("id", "\"X\"", false, "java.lang.String")))
            .build();
    var withoutTypes =
        withTypes.toBuilder()
            .ntReturnType(null)
            .ntParameters(List.of(new ParameterEntry("id", "\"X\"", false)))
            .build();

    assertThat(CanonicalEntrySerializer.toJson(withTypes))
        .contains("\"nt.returnType\": \"java.lang.String\"")
        .contains("\"type\": \"java.lang.String\"");
    assertThat(CanonicalEntrySerializer.toJson(withoutTypes))
        .doesNotContain("\"nt.returnType\"")
        .doesNotContain("\"type\"");
  }

  @Test
  void writesThreadFieldsWhenPresentAndOmitsThemWhenNull() {
    var withThread =
        CanonicalEntry.builder()
            .timestamp("2026-03-19T10:23:01.123Z")
            .level("trace")
            .service("order-service")
            .message("→ OrderService.placeOrder()")
            .ntEntryType("entry")
            .ntEventType("method_enter")
            .ntSchemaVersion("1.2")
            .threadName("worker-3")
            .threadId(42L)
            .ntThreadVirtual(true)
            .build();
    var withoutThread =
        withThread.toBuilder().threadName(null).threadId(null).ntThreadVirtual(null).build();

    assertThat(CanonicalEntrySerializer.toJson(withThread))
        .contains("\"thread.name\": \"worker-3\"")
        .contains("\"thread.id\": 42")
        .contains("\"nt.threadVirtual\": true");
    assertThat(CanonicalEntrySerializer.toJson(withoutThread))
        .doesNotContain("\"thread.name\"")
        .doesNotContain("\"thread.id\"")
        .doesNotContain("\"nt.threadVirtual\"");
  }

  @Test
  void writesResourceFieldsWhenPresentAndOmitsThemWhenNull() {
    var withResource =
        CanonicalEntry.builder()
            .timestamp("2026-03-19T10:23:01.123Z")
            .level("trace")
            .service("order-service")
            .message("→ OrderService.placeOrder()")
            .ntEntryType("entry")
            .ntEventType("method_enter")
            .ntSchemaVersion("1.2")
            .hostName("web-1")
            .processPid(4242L)
            .runtimeVersion("17.0.10+7")
            .build();
    var withoutResource =
        withResource.toBuilder().hostName(null).processPid(null).runtimeVersion(null).build();

    assertThat(CanonicalEntrySerializer.toJson(withResource))
        .contains("\"host.name\": \"web-1\"")
        .contains("\"process.pid\": 4242")
        .contains("\"process.runtime.version\": \"17.0.10+7\"");
    assertThat(CanonicalEntrySerializer.toJson(withoutResource))
        .doesNotContain("\"host.name\"")
        .doesNotContain("\"process.pid\"")
        .doesNotContain("\"process.runtime.version\"");
  }

  @Test
  void writesSourceLocationAndInstanceIdWhenPresentAndOmitsThemWhenNull() {
    var withLocation =
        CanonicalEntry.builder()
            .timestamp("2026-03-19T10:23:01.123Z")
            .level("trace")
            .service("order-service")
            .message("→ OrderService.placeOrder()")
            .ntEntryType("entry")
            .ntEventType("method_enter")
            .ntSchemaVersion("1.2")
            .codeFilepath("OrderService.java")
            .codeLineno(42)
            .ntInstanceId("1a2b3c4d")
            .build();
    var withoutLocation =
        withLocation.toBuilder().codeFilepath(null).codeLineno(null).ntInstanceId(null).build();

    assertThat(CanonicalEntrySerializer.toJson(withLocation))
        .contains("\"code.filepath\": \"OrderService.java\"")
        .contains("\"code.lineno\": 42")
        .contains("\"nt.instanceId\": \"1a2b3c4d\"");
    assertThat(CanonicalEntrySerializer.toJson(withoutLocation))
        .doesNotContain("\"code.filepath\"")
        .doesNotContain("\"code.lineno\"")
        .doesNotContain("\"nt.instanceId\"");
  }

  @Test
  void writesNarrationTemplateWhenPresentAndOmitsItWhenNull() {
    var withTemplate =
        CanonicalEntry.builder()
            .timestamp("2026-03-19T10:23:01.123Z")
            .level("trace")
            .message("Opening for C-123")
            .service("order-service")
            .environment("production")
            .traceId("0123456789abcdef0123456789abcdef")
            .spanId("0123456789abcdef")
            .codeNamespace("OverdraftService")
            .codeFunction("openAccount")
            .ntEntryType("entry")
            .ntEventType("method_enter")
            .ntSchemaVersion("1.1")
            .ntTraceName("bold elk soars")
            .ntNarrationTemplate("Opening for {customerId}")
            .build();

    var json = CanonicalEntrySerializer.toJson(withTemplate);

    assertThat(json).contains("\"nt.narrationTemplate\": \"Opening for {customerId}\"");
  }

  @Test
  void omitsNullOptionalFields() {
    var entry =
        CanonicalEntry.builder()
            .timestamp("2026-03-19T10:23:01.123Z")
            .level("trace")
            .message("msg")
            .service("svc")
            .traceId("0123456789abcdef0123456789abcdef")
            .spanId("0123456789abcdef")
            .codeNamespace("Svc")
            .codeFunction("m")
            .ntEntryType("entry")
            .ntEventType("method_enter")
            .ntSchemaVersion("1.1")
            .build();

    var json = CanonicalEntrySerializer.toJson(entry);

    assertThat(json).doesNotContain("\"environment\"");
    assertThat(json).doesNotContain("\"nt.outcome\"");
    assertThat(json).doesNotContain("\"nt.returnValue\"");
    assertThat(json).doesNotContain("\"exception.type\"");
    assertThat(json).doesNotContain("\"nt.forkId\"");
    assertThat(json).doesNotContain("\"nt.causalId\"");
    assertThat(json).doesNotContain("\"durationMs\"");
  }

  @Test
  void serializesParametersAsArray() {
    var entry =
        CanonicalEntry.builder()
            .timestamp("2026-03-19T10:23:01.123Z")
            .level("trace")
            .message("msg")
            .service("svc")
            .traceId("0123456789abcdef0123456789abcdef")
            .spanId("0123456789abcdef")
            .codeNamespace("Svc")
            .codeFunction("m")
            .ntEntryType("entry")
            .ntEventType("method_enter")
            .ntSchemaVersion("1.1")
            .ntParameters(
                List.of(
                    new ParameterEntry("id", "\"42\"", false),
                    new ParameterEntry("secret", "[REDACTED]", true)))
            .build();

    var json = CanonicalEntrySerializer.toJson(entry);

    assertThat(json).contains("\"nt.parameters\": [");
    assertThat(json).contains("\"name\": \"id\"");
    assertThat(json).contains("\"value\": \"\\\"42\\\"\"");
    assertThat(json).contains("\"redacted\": false");
    assertThat(json).contains("\"redacted\": true");
  }

  @Test
  void serializesErrorFields() {
    var entry =
        CanonicalEntry.builder()
            .timestamp("2026-03-19T10:23:01.123Z")
            .level("error")
            .message("!! Error")
            .service("svc")
            .traceId("0123456789abcdef0123456789abcdef")
            .spanId("0123456789abcdef")
            .codeNamespace("Svc")
            .codeFunction("m")
            .ntEntryType("entry")
            .ntEventType("method_exit")
            .ntSchemaVersion("1.1")
            .ntOutcome("failure")
            .exceptionType("IllegalArgumentException")
            .exceptionMessage("bad input")
            .build();

    var json = CanonicalEntrySerializer.toJson(entry);

    assertThat(json).contains("\"exception.type\": \"IllegalArgumentException\"");
    assertThat(json).contains("\"exception.message\": \"bad input\"");
    assertThat(json).contains("\"nt.outcome\": \"failure\"");
  }

  @Test
  void serializesForkIdAndBranchIndex() {
    var entry =
        CanonicalEntry.builder()
            .timestamp("2026-03-19T10:23:01.123Z")
            .level("trace")
            .service("order-service")
            .message("fork [g1]")
            .ntEntryType("entry")
            .ntEventType("fork")
            .ntSchemaVersion("1.1")
            .ntForkId("g1")
            .ntBranchIndex(2)
            .build();

    var json = CanonicalEntrySerializer.toJson(entry);

    assertThat(json).contains("\"nt.forkId\": \"g1\"");
    assertThat(json).contains("\"nt.branchIndex\": 2");
  }

  @Test
  void serializesDurationAndParentSpanId() {
    var entry =
        CanonicalEntry.builder()
            .timestamp("2026-03-19T10:23:01.123Z")
            .level("trace")
            .message("msg")
            .service("svc")
            .traceId("0123456789abcdef0123456789abcdef")
            .spanId("0123456789abcdef")
            .parentSpanId("fedcba9876543210")
            .codeNamespace("Svc")
            .codeFunction("m")
            .ntEntryType("entry")
            .ntEventType("method_exit")
            .ntSchemaVersion("1.1")
            .ntOutcome("success")
            .durationMs(251L)
            .ntReturnValue("42")
            .build();

    var json = CanonicalEntrySerializer.toJson(entry);

    assertThat(json).contains("\"durationMs\": 251");
    assertThat(json).contains("\"parent_span_id\": \"fedcba9876543210\"");
    assertThat(json).contains("\"nt.outcome\": \"success\"");
    assertThat(json).contains("\"nt.returnValue\": \"42\"");
  }

  @Test
  void serializesCausalId() {
    var entry =
        CanonicalEntry.builder()
            .timestamp("2026-03-19T10:23:01.123Z")
            .level("trace")
            .message("msg")
            .service("svc")
            .traceId("0123456789abcdef0123456789abcdef")
            .spanId("0123456789abcdef")
            .codeNamespace("Svc")
            .codeFunction("m")
            .ntEntryType("entry")
            .ntEventType("fork")
            .ntSchemaVersion("1.1")
            .ntCausalId("causal-123")
            .build();

    var json = CanonicalEntrySerializer.toJson(entry);

    assertThat(json).contains("\"nt.causalId\": \"causal-123\"");
  }

  @Test
  void escapesSpecialCharactersInStrings() {
    var entry =
        CanonicalEntry.builder()
            .timestamp("2026-03-19T10:23:01.123Z")
            .level("trace")
            .message("msg with \"quotes\" and \\backslash")
            .service("svc")
            .traceId("0123456789abcdef0123456789abcdef")
            .spanId("0123456789abcdef")
            .codeNamespace("Svc")
            .codeFunction("m")
            .ntEntryType("entry")
            .ntEventType("method_enter")
            .ntSchemaVersion("1.1")
            .build();

    var json = CanonicalEntrySerializer.toJson(entry);

    assertThat(json).contains("\"message\": \"msg with \\\"quotes\\\" and \\\\backslash\"");
  }

  // ── required-field guard ─────────────────────────────────────────────────────

  @Test
  void nullInARequiredFieldFailsLoudlyInsteadOfEmittingTheStringNull() {
    var entry = CanonicalEntry.builder().timestamp("2026-01-01T00:00:00Z").level("trace").build();

    assertThatThrownBy(() -> CanonicalEntrySerializer.toJson(entry))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("message");
  }

  @Test
  void aRequiredFieldNeverSerializesToTheLiteralStringNull() {
    var entry =
        CanonicalEntry.builder()
            .timestamp("2026-01-01T00:00:00Z")
            .level("trace")
            .message("m")
            .service(null)
            .ntEntryType("entry")
            .ntEventType("method_enter")
            .ntSchemaVersion("1.2")
            .build();

    assertThatThrownBy(() -> CanonicalEntrySerializer.toJson(entry))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("service");
  }
}
