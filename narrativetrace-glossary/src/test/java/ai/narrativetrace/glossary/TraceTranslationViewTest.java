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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TraceTranslationViewTest {

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

  private static Glossary billingGlossary(GlossaryTerm... terms) {
    return new Glossary(
        1,
        Map.of("billing", new BoundedContext("billing", List.of("com.acme.billing"), null)),
        List.of(terms));
  }

  private static CanonicalEntry enter(
      String className,
      String method,
      String spanId,
      String parentSpanId,
      ParameterEntry... params) {
    return CanonicalEntry.builder()
        .timestamp("2026-08-14T10:00:00.000Z")
        .level("trace")
        .message("→ " + className + "." + method)
        .traceId("0123456789abcdef0123456789abcdef")
        .spanId(spanId)
        .parentSpanId(parentSpanId)
        .codeNamespace(className)
        .codeFunction(method)
        .ntEntryType("entry")
        .ntEventType("method_enter")
        .ntSchemaVersion("1.1")
        .ntParameters(params.length == 0 ? null : List.of(params))
        .build();
  }

  private static TraceTranslationView billingView(Glossary glossary) {
    return new TraceTranslationView(glossary, className -> "com.acme.billing");
  }

  private static CanonicalEntry exit(
      String className,
      String spanId,
      String outcome,
      String returnValue,
      String exceptionType,
      String exceptionMessage) {
    return CanonicalEntry.builder()
        .timestamp("2026-08-14T10:00:00.100Z")
        .level("failure".equals(outcome) ? "error" : "trace")
        .message("exit")
        .traceId("0123456789abcdef0123456789abcdef")
        .spanId(spanId)
        .codeNamespace(className)
        .codeFunction("")
        .ntEntryType("entry")
        .ntEventType("method_exit")
        .ntSchemaVersion("1.1")
        .ntOutcome(outcome)
        .ntReturnValue(returnValue)
        .exceptionType(exceptionType)
        .exceptionMessage(exceptionMessage)
        .build();
  }

  @Test
  void rendersTheEnterLineWithGlossedIdentifiersAndVerbatimValues() {
    var glossary =
        billingGlossary(
            term("charge", TermKind.VERB_PHRASE, Map.of("es", "cobrar")),
            term("customer", TermKind.WORD, Map.of("es", "cliente")),
            term("amount", TermKind.WORD, Map.of("es", "importe")));
    var entries =
        List.of(
            enter(
                "PaymentService",
                "charge",
                "0000000000000001",
                null,
                new ParameterEntry("customerId", "\"C-BROKE\"", false),
                new ParameterEntry("amount", "74.97", false)));

    var text = billingView(glossary).render(entries, "es");

    assertThat(text)
        .contains("PaymentService.cobrar (charge) (cliente: \"C-BROKE\", importe: 74.97)");
  }

  @Test
  void untranslatedIdentifiersRenderAsIsAndFeedTheGapsFooter() {
    var entries =
        List.of(
            enter(
                "PaymentService",
                "charge",
                "0000000000000001",
                null,
                new ParameterEntry("customerId", "\"C-1\"", false)));

    var text = billingView(billingGlossary()).render(entries, "es");

    assertThat(text).contains("PaymentService.charge(customerId: \"C-1\")");
    assertThat(text).contains("Vacíos del glosario");
    assertThat(text).contains("- charge");
    assertThat(text).contains("- customer");
  }

  @Test
  void failureExitTranslatesTheExceptionPhraseAndKeepsTheMessageVerbatim() {
    var glossary =
        billingGlossary(
            term("charge", TermKind.VERB_PHRASE, Map.of("es", "cobrar")),
            term("insufficient fund", TermKind.NOUN_PHRASE, Map.of("es", "fondos insuficientes")));
    var entries =
        List.of(
            enter("PaymentService", "charge", "0000000000000001", null),
            exit(
                "PaymentService",
                "0000000000000001",
                "failure",
                null,
                "InsufficientFundsException",
                "balance 12.50 below required 74.97"));

    var text = billingView(glossary).render(entries, "es");

    assertThat(text)
        .contains(
            "  !! fondos insuficientes [InsufficientFundsException]:"
                + " balance 12.50 below required 74.97");
  }

  @Test
  void successExitRendersTheReturnsLabelAndTheVerbatimValue() {
    var glossary = billingGlossary(term("charge", TermKind.VERB_PHRASE, Map.of("es", "cobrar")));
    var entries =
        List.of(
            enter("PaymentService", "charge", "0000000000000001", null),
            exit("PaymentService", "0000000000000001", "success", "\"TXN-1\"", null, null));

    var text = billingView(glossary).render(entries, "es");

    assertThat(text).contains("  -> devuelve \"TXN-1\"");
  }

  @Test
  void nestedCallsIndentByParentSpanChain() {
    var glossary = billingGlossary(term("charge", TermKind.VERB_PHRASE, Map.of("es", "cobrar")));
    var entries =
        List.of(
            enter("OrderService", "placeOrder", "0000000000000001", null),
            enter("PaymentService", "charge", "0000000000000002", "0000000000000001"));

    var text = billingView(glossary).render(entries, "es");

    assertThat(text).contains("\n  PaymentService.cobrar (charge) ()");
    assertThat(text).contains("OrderService.placeOrder(");
  }

  private static CanonicalEntry enterWithTemplate(
      String className, String method, String spanId, String template, ParameterEntry... params) {
    return CanonicalEntry.builder()
        .timestamp("2026-08-14T10:00:00.000Z")
        .level("trace")
        .message("→ " + className + "." + method)
        .traceId("0123456789abcdef0123456789abcdef")
        .spanId(spanId)
        .codeNamespace(className)
        .codeFunction(method)
        .ntEntryType("entry")
        .ntEventType("method_enter")
        .ntSchemaVersion("1.1")
        .ntParameters(params.length == 0 ? null : List.of(params))
        .ntNarrationTemplate(template)
        .build();
  }

  private static GlossaryTerm templateTerm(String rawTemplate, Map<String, String> translations) {
    return new GlossaryTerm(
        rawTemplate,
        "billing",
        TermKind.TEMPLATE,
        TermStatus.CURATED,
        null,
        translations,
        List.of(),
        List.of(),
        LocalDate.of(2026, 8, 11));
  }

  @Test
  void narrationTemplateRendersItsLocaleVariantFilledWithVerbatimValues() {
    var glossary =
        billingGlossary(
            term("charge", TermKind.VERB_PHRASE, Map.of("es", "cobrar")),
            templateTerm(
                "Charging {amount} to {customerId}",
                Map.of("es", "Cobrando {amount} a {customerId}")));
    var entries =
        List.of(
            enterWithTemplate(
                "PaymentService",
                "charge",
                "0000000000000001",
                "Charging {amount} to {customerId}",
                new ParameterEntry("amount", "74.97", false),
                new ParameterEntry("customerId", "\"C-1\"", false)));

    var text = billingView(glossary).render(entries, "es");

    assertThat(text).contains("\n  Cobrando 74.97 a \"C-1\"\n");
  }

  @Test
  void narrationTemplateWithoutALocaleVariantBecomesAGapAndRendersNoNarrationLine() {
    var glossary = billingGlossary(term("charge", TermKind.VERB_PHRASE, Map.of("es", "cobrar")));
    var entries =
        List.of(
            enterWithTemplate(
                "PaymentService",
                "charge",
                "0000000000000001",
                "Charging {amount} to {customerId}",
                new ParameterEntry("amount", "74.97", false)));

    var text = billingView(glossary).render(entries, "es");

    assertThat(text).doesNotContain("Cobrando");
    assertThat(text).contains("- Charging {amount} to {customerId}");
  }

  @Test
  void incompleteExitRendersTheIncompleteLabel() {
    var glossary = billingGlossary(term("charge", TermKind.VERB_PHRASE, Map.of("es", "cobrar")));
    var entries =
        List.of(
            enter("PaymentService", "charge", "0000000000000001", null),
            exit("PaymentService", "0000000000000001", "incomplete", null, null, null));

    var text = billingView(glossary).render(entries, "es");

    assertThat(text).contains("  .. incompleto");
  }

  @Test
  void redactedParameterValuesPassThroughVerbatim() {
    var glossary = billingGlossary(term("password", TermKind.WORD, Map.of("es", "contraseña")));
    var entries =
        List.of(
            enter(
                "AuthService",
                "login",
                "0000000000000001",
                null,
                new ParameterEntry("password", "[REDACTED]", true)));

    var text = billingView(glossary).render(entries, "es");

    assertThat(text).contains("contraseña: [REDACTED]");
  }

  @Test
  void untranslatedExceptionPhraseFallsBackToTheOriginalTypeAndBecomesAGap() {
    var entries =
        List.of(
            enter("PaymentService", "charge", "0000000000000001", null),
            exit(
                "PaymentService",
                "0000000000000001",
                "failure",
                null,
                "InsufficientFundsException",
                "balance low"));

    var text = billingView(billingGlossary()).render(entries, "es");

    assertThat(text).contains("  !! InsufficientFundsException: balance low");
    assertThat(text).doesNotContain("[InsufficientFundsException]");
    assertThat(text).contains("- insufficient fund");
  }

  @Test
  void suffixOnlyExceptionTypeRendersAsIs() {
    var entries =
        List.of(
            enter("PaymentService", "charge", "0000000000000001", null),
            exit("PaymentService", "0000000000000001", "failure", null, "Exception", "boom"));

    var text = billingView(billingGlossary()).render(entries, "es");

    assertThat(text).contains("  !! Exception: boom");
  }

  @Test
  void failureWithoutAnExceptionTypeStillRendersTheMessage() {
    var entries =
        List.of(
            enter("PaymentService", "charge", "0000000000000001", null),
            exit("PaymentService", "0000000000000001", "failure", null, null, "boom"));

    var text = billingView(billingGlossary()).render(entries, "es");

    assertThat(text).contains("  !! : boom");
  }

  @Test
  void bareIdParameterKeepsItsName() {
    var entries =
        List.of(
            enter(
                "PaymentService",
                "charge",
                "0000000000000001",
                null,
                new ParameterEntry("id", "\"1\"", false)));

    var text = billingView(billingGlossary()).render(entries, "es");

    assertThat(text).contains("(id: \"1\")");
  }

  @Test
  void rejectsANullGlossaryOrPackageResolver() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new TraceTranslationView(null, className -> null))
        .isInstanceOf(IllegalArgumentException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new TraceTranslationView(billingGlossary(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullEntriesAndBlankLocale() {
    var view = billingView(billingGlossary());

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> view.render(null, "es"))
        .isInstanceOf(IllegalArgumentException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> view.render(List.of(), " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void singleContextGlossaryTranslatesEvenWithoutPackageInformation() {
    var glossary = billingGlossary(term("charge", TermKind.VERB_PHRASE, Map.of("es", "cobrar")));
    var view = new TraceTranslationView(glossary, className -> null);

    var text =
        view.render(List.of(enter("PaymentService", "charge", "0000000000000001", null)), "es");

    assertThat(text).contains("PaymentService.cobrar (charge)");
  }

  @Test
  void multiContextGlossaryStaysStrictWithoutPackageInformation() {
    var glossary =
        new Glossary(
            1,
            Map.of(
                "billing", new BoundedContext("billing", List.of("com.acme.billing"), null),
                "insurance", new BoundedContext("insurance", List.of("com.acme.insurance"), null)),
            List.of(term("charge", TermKind.VERB_PHRASE, Map.of("es", "cobrar"))));
    var view = new TraceTranslationView(glossary, className -> null);

    var text =
        view.render(List.of(enter("PaymentService", "charge", "0000000000000001", null)), "es");

    assertThat(text).contains("PaymentService.charge(");
    assertThat(text).doesNotContain("cobrar");
  }

  @Test
  void renderEntryRendersASingleEnterAgainstMutableState() {
    var glossary = billingGlossary(term("charge", TermKind.VERB_PHRASE, Map.of("es", "cobrar")));
    var view = billingView(glossary);
    var state = view.newRenderState("es");

    var text = view.renderEntry(enter("PaymentService", "charge", "0000000000000001", null), state);

    assertThat(text).isEqualTo("PaymentService.cobrar (charge) ()\n");
  }

  @Test
  void renderEntryAccumulatesDepthAcrossCallsOnTheSameState() {
    var glossary = billingGlossary(term("charge", TermKind.VERB_PHRASE, Map.of("es", "cobrar")));
    var view = billingView(glossary);
    var state = view.newRenderState("es");

    view.renderEntry(enter("OrderService", "placeOrder", "0000000000000001", null), state);
    var child =
        view.renderEntry(
            enter("PaymentService", "charge", "0000000000000002", "0000000000000001"), state);
    var exit =
        view.renderEntry(
            exit("PaymentService", "0000000000000002", "success", "\"TXN-1\"", null, null), state);

    assertThat(child).isEqualTo("  PaymentService.cobrar (charge) ()\n");
    assertThat(exit).isEqualTo("    -> devuelve \"TXN-1\"\n");
  }

  @Test
  void gapRegistryAccumulatesAcrossEntriesAndRendersSortedOnce() {
    var view = billingView(billingGlossary());
    var state = view.newRenderState("es");

    var enterText =
        view.renderEntry(
            enter(
                "PaymentService",
                "charge",
                "0000000000000001",
                null,
                new ParameterEntry("customerId", "\"C-1\"", false)),
            state);
    view.renderEntry(enter("OrderService", "audit", "0000000000000002", null), state);

    assertThat(enterText).doesNotContain("Vacíos del glosario");
    assertThat(view.renderGapsFooter(state))
        .isEqualTo("\n---\nVacíos del glosario:\n- audit\n- charge\n- customer\n");
  }

  @Test
  void gapsFooterIsEmptyWhenEverythingTranslated() {
    var glossary = billingGlossary(term("charge", TermKind.VERB_PHRASE, Map.of("es", "cobrar")));
    var view = billingView(glossary);
    var state = view.newRenderState("es");

    view.renderEntry(enter("PaymentService", "charge", "0000000000000001", null), state);

    assertThat(view.renderGapsFooter(state)).isEmpty();
  }

  @Test
  void lateExitWhoseEnterWasNeverSeenRendersAtDepthZero() {
    var view = billingView(billingGlossary());
    var state = view.newRenderState("es");

    var text =
        view.renderEntry(
            exit("PaymentService", "00000000000000ff", "success", "\"TXN-9\"", null, null), state);

    assertThat(text).isEqualTo("  -> devuelve \"TXN-9\"\n");
  }

  @Test
  void lateEnterWithAnUnknownParentRendersAtDepthZero() {
    var glossary = billingGlossary(term("charge", TermKind.VERB_PHRASE, Map.of("es", "cobrar")));
    var view = billingView(glossary);
    var state = view.newRenderState("es");

    var text =
        view.renderEntry(
            enter("PaymentService", "charge", "0000000000000002", "00000000000000ff"), state);

    assertThat(text).isEqualTo("PaymentService.cobrar (charge) ()\n");
  }

  @Test
  void rejectsNullArgumentsOnThePerEntryApi() {
    var view = billingView(billingGlossary());
    var state = view.newRenderState("es");
    var entry = enter("PaymentService", "charge", "0000000000000001", null);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> view.renderEntry(null, state))
        .isInstanceOf(IllegalArgumentException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> view.renderEntry(entry, null))
        .isInstanceOf(IllegalArgumentException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> view.renderGapsFooter(null))
        .isInstanceOf(IllegalArgumentException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> view.newRenderState(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void glossaryGuardFiresBeforeThePackageResolverGuard() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TraceTranslationView(null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("glossary must not be null");
  }

  @Test
  void declaredUnassignedContextDoesNotBlockTheSingleContextFallback() {
    var glossary =
        new Glossary(
            1,
            Map.of(
                "billing", new BoundedContext("billing", List.of("com.acme.billing"), null),
                "_unassigned", new BoundedContext("_unassigned", List.of(), null)),
            List.of(term("charge", TermKind.VERB_PHRASE, Map.of("es", "cobrar"))));
    var view = new TraceTranslationView(glossary, className -> null);

    var text =
        view.render(List.of(enter("PaymentService", "charge", "0000000000000001", null)), "es");

    assertThat(text).contains("PaymentService.cobrar (charge)");
  }

  @Test
  void multiContextGlossaryTranslatesThroughTheResolvedPackage() {
    var insuranceTerm =
        new GlossaryTerm(
            "settle",
            "insurance",
            TermKind.VERB_PHRASE,
            TermStatus.CURATED,
            null,
            Map.of("es", "liquidar"),
            List.of(),
            List.of(),
            LocalDate.of(2026, 8, 11));
    var glossary =
        new Glossary(
            1,
            Map.of(
                "billing", new BoundedContext("billing", List.of("com.acme.billing"), null),
                "insurance", new BoundedContext("insurance", List.of("com.acme.insurance"), null)),
            List.of(insuranceTerm));
    var view = new TraceTranslationView(glossary, className -> "com.acme.insurance");

    var text =
        view.render(List.of(enter("ClaimService", "settle", "0000000000000001", null)), "es");

    assertThat(text).contains("ClaimService.liquidar (settle)");
  }

  private static Glossary billingAndInsuranceGlossary(GlossaryTerm... terms) {
    return new Glossary(
        1,
        Map.of(
            "billing", new BoundedContext("billing", List.of("com.acme.billing"), null),
            "insurance", new BoundedContext("insurance", List.of("com.acme.insurance"), null)),
        List.of(terms));
  }

  private static GlossaryTerm insuranceTerm(
      String term, TermKind kind, Map<String, String> translations) {
    return new GlossaryTerm(
        term,
        "insurance",
        kind,
        TermStatus.CURATED,
        null,
        translations,
        List.of(),
        List.of(),
        LocalDate.of(2026, 8, 11));
  }

  @Test
  void capturedEntryPackageOverridesThePackageResolver() {
    var glossary =
        billingAndInsuranceGlossary(
            insuranceTerm("settle", TermKind.VERB_PHRASE, Map.of("es", "liquidar")));
    var view = new TraceTranslationView(glossary, className -> "com.acme.billing");
    var entry =
        enter("ClaimService", "settle", "0000000000000001", null).toBuilder()
            .ntPackage("com.acme.insurance")
            .build();

    var text = view.render(List.of(entry), "es");

    assertThat(text).contains("ClaimService.liquidar (settle)");
  }

  @Test
  void capturedExitPackageOverridesThePackageResolverForExceptionPhrases() {
    var glossary =
        billingAndInsuranceGlossary(
            insuranceTerm(
                "claim rejected", TermKind.NOUN_PHRASE, Map.of("es", "siniestro rechazado")));
    var view = new TraceTranslationView(glossary, className -> "com.acme.billing");
    var failure =
        exit("ClaimService", "0000000000000001", "failure", null, "ClaimRejectedException", "boom")
            .toBuilder()
            .ntPackage("com.acme.insurance")
            .build();

    var text = view.render(List.of(failure), "es");

    assertThat(text).contains("!! siniestro rechazado [ClaimRejectedException]: boom");
  }

  @Test
  void fullyTranslatedTraceHasNoGapsFooter() {
    var glossary = billingGlossary(term("charge", TermKind.VERB_PHRASE, Map.of("es", "cobrar")));
    var entries = List.of(enter("PaymentService", "charge", "0000000000000001", null));

    var text = billingView(glossary).render(entries, "es");

    assertThat(text).doesNotContain("Vacíos del glosario");
  }
}
