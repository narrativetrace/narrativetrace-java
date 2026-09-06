/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.export.CanonicalEntry;
import ai.narrativetrace.core.export.ParameterEntry;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Parity properties of the per-entry rendering API: the batch {@code render} must stay
 * byte-identical to concatenating {@code renderEntry} over one state plus the gaps footer (the live
 * subscriber contract), and a view must be reusable — rendering the same entries twice yields the
 * same output, so no state leaks across render sequences. Entry sequences deliberately include
 * malformed shapes (null span ids, unknown parents, exits without enters, unknown event types).
 */
class TraceTranslationViewProperties {

  @Property
  void batchRenderEqualsConcatenatedPerEntryRendersPlusGapsFooter(
      @ForAll("glossaries") Glossary glossary,
      @ForAll("entrySequences") List<CanonicalEntry> entries,
      @ForAll("locales") String locale) {
    var view = new TraceTranslationView(glossary, className -> "com.acme.billing");

    var state = view.newRenderState(locale);
    var incremental = new StringBuilder();
    for (var entry : entries) {
      incremental.append(view.renderEntry(entry, state));
    }
    incremental.append(view.renderGapsFooter(state));
    var batch = view.render(entries, locale);

    assertThat(batch).isEqualTo(incremental.toString());
    assertThat(view.render(entries, locale)).isEqualTo(batch);
  }

  @Provide
  Arbitrary<Glossary> glossaries() {
    return GlossaryArbitraries.glossaries();
  }

  @Provide
  Arbitrary<String> locales() {
    return Arbitraries.of("es", "de", "zh-CN");
  }

  @Provide
  Arbitrary<List<CanonicalEntry>> entrySequences() {
    return Arbitraries.oneOf(enters(), exits()).list().ofMaxSize(8);
  }

  private Arbitrary<CanonicalEntry> enters() {
    return Combinators.combine(
            Arbitraries.of("PaymentService", "OrderService"),
            Arbitraries.of("charge", "placeOrder", "alpha", "beta"),
            spanIds(),
            spanIds(),
            Arbitraries.of("Charging {amount} to {customerId}", "Auditing {amount}")
                .injectNull(0.5),
            parameters())
        .as(TraceTranslationViewProperties::enter);
  }

  private Arbitrary<CanonicalEntry> exits() {
    return Combinators.combine(
            Arbitraries.of("PaymentService", "OrderService"),
            spanIds(),
            Arbitraries.of("success", "failure", "incomplete", "unknown"),
            Arbitraries.of("\"TXN-1\"", "42").injectNull(0.5),
            Arbitraries.of("AlphaException", "Exception").injectNull(0.5),
            Arbitraries.of("balance low", "boom").injectNull(0.5))
        .as(TraceTranslationViewProperties::exit);
  }

  private Arbitrary<String> spanIds() {
    return Arbitraries.of("0000000000000001", "0000000000000002", "0000000000000003")
        .injectNull(0.2);
  }

  private Arbitrary<List<ParameterEntry>> parameters() {
    return Combinators.combine(
            Arbitraries.of("amount", "customerId", "id"),
            Arbitraries.of("74.97", "\"C-1\"", "[REDACTED]"))
        .as((name, value) -> new ParameterEntry(name, value, false))
        .list()
        .uniqueElements(ParameterEntry::name)
        .ofMaxSize(2)
        .map(params -> params.isEmpty() ? null : params);
  }

  private static CanonicalEntry enter(
      String className,
      String method,
      String spanId,
      String parentSpanId,
      String template,
      List<ParameterEntry> params) {
    return CanonicalEntry.builder()
        .timestamp("2026-08-15T10:00:00.000Z")
        .level("trace")
        .message("→ " + className + "." + method)
        .traceId("0123456789abcdef0123456789abcdef")
        .spanId(spanId)
        .parentSpanId(parentSpanId)
        .codeNamespace(className)
        .codeFunction(method)
        .ntEntryType("entry")
        .ntEventType("method_enter")
        .ntSchemaVersion("1.2")
        .ntParameters(params)
        .ntNarrationTemplate(template)
        .build();
  }

  private static CanonicalEntry exit(
      String className,
      String spanId,
      String outcome,
      String returnValue,
      String exceptionType,
      String exceptionMessage) {
    return CanonicalEntry.builder()
        .timestamp("2026-08-15T10:00:00.100Z")
        .level("failure".equals(outcome) ? "error" : "trace")
        .message("exit")
        .traceId("0123456789abcdef0123456789abcdef")
        .spanId(spanId)
        .codeNamespace(className)
        .codeFunction("")
        .ntEntryType("entry")
        .ntEventType("method_exit")
        .ntSchemaVersion("1.2")
        .ntOutcome(outcome)
        .ntReturnValue(returnValue)
        .exceptionType(exceptionType)
        .exceptionMessage(exceptionMessage)
        .build();
  }
}
