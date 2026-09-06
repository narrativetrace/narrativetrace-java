/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.export.CanonicalEntry;
import ai.narrativetrace.core.export.ParameterEntry;
import ai.narrativetrace.glossary.BoundedContext;
import ai.narrativetrace.glossary.Glossary;
import ai.narrativetrace.glossary.GlossaryTerm;
import ai.narrativetrace.glossary.TermKind;
import ai.narrativetrace.glossary.TermStatus;
import ai.narrativetrace.glossary.TraceTranslationView;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Walkthrough 3 of the glossary plan, exercised from outside the production package: a failed
 * charge translated to Spanish, with values and the runtime exception message byte-identical and
 * original identifiers greppable beside their glosses.
 */
class TranslatedTraceWalkthroughTest {

  private static GlossaryTerm term(String term, TermKind kind, Map<String, String> translations) {
    return new GlossaryTerm(
        term,
        "billing",
        kind,
        TermStatus.CURATED,
        null,
        translations,
        List.of(),
        List.of(),
        LocalDate.of(2026, 8, 11));
  }

  @Test
  void walkthroughThreeTranslatesTheFailedChargeForSupport() {
    var glossary =
        new Glossary(
            1,
            Map.of("billing", new BoundedContext("billing", List.of("com.acme.billing"), null)),
            List.of(
                term("charge", TermKind.VERB_PHRASE, Map.of("es", "cobrar")),
                term("customer", TermKind.WORD, Map.of("es", "cliente")),
                term("amount", TermKind.WORD, Map.of("es", "importe")),
                term(
                    "insufficient fund",
                    TermKind.NOUN_PHRASE,
                    Map.of("es", "fondos insuficientes"))));
    var entries =
        List.of(
            entry(
                "method_enter",
                "PaymentService",
                "charge",
                List.of(
                    new ParameterEntry("customerId", "\"C-BROKE\"", false),
                    new ParameterEntry("amount", "74.97", false)),
                null,
                null,
                null),
            entry(
                "method_exit",
                "PaymentService",
                "",
                null,
                "failure",
                "InsufficientFundsException",
                "balance 12.50 below required 74.97"));

    var view = new TraceTranslationView(glossary, className -> "com.acme.billing");
    var text = view.render(entries, "es");

    assertThat(text)
        .isEqualTo(
            "PaymentService.cobrar (charge) (cliente: \"C-BROKE\", importe: 74.97)\n"
                + "  !! fondos insuficientes [InsufficientFundsException]:"
                + " balance 12.50 below required 74.97\n");
  }

  private static CanonicalEntry entry(
      String eventType,
      String className,
      String method,
      List<ParameterEntry> params,
      String outcome,
      String exceptionType,
      String exceptionMessage) {
    return CanonicalEntry.builder()
        .timestamp("2026-08-14T10:00:00.000Z")
        .level(exceptionType != null ? "error" : "trace")
        .message("message is never parsed")
        .traceId("0123456789abcdef0123456789abcdef")
        .spanId("0000000000000001")
        .codeNamespace(className)
        .codeFunction(method)
        .ntEntryType("entry")
        .ntEventType(eventType)
        .ntSchemaVersion("1.1")
        .ntOutcome(outcome)
        .ntParameters(params)
        .exceptionType(exceptionType)
        .exceptionMessage(exceptionMessage)
        .build();
  }
}
