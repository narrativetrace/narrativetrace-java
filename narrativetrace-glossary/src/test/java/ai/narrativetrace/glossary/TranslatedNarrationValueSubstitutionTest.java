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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADV-2026-10-30-6 — the translated narration line is filled by replacing one placeholder at a time
 * in the accumulating string, so a captured value that looks like a placeholder is itself
 * substituted into. A value is altered, another parameter's value lands in a position the template
 * did not ask for, and the same two values render differently depending only on the order the
 * parameters were captured in.
 *
 * <p>INTENT: this class states the rule it breaks as its own load-bearing one — "every line is
 * rebuilt from structural fields plus verbatim values, so no value token ({@code
 * nt.parameters[].value}, {@code nt.returnValue}, {@code exception.message}) is ever altered". Core
 * resolves the same {@code @Narrated} templates the other way and cannot have this defect: {@code
 * TemplateParser} parses a template once into sealed {@code Segment}s and resolves each segment
 * against the values, so a resolved value is never re-scanned. The two paths resolve the same
 * annotation text, which is the parity this repository treats as a defect class of its own.
 *
 * <p><b>@llmNote</b> The input is a captured runtime value, so the host program chooses it: a
 * template placeholder is ordinary text in a product name, a serialized map, a log line a service
 * returned, or any value a renderer wrote braces around. The translated view is also the one output
 * where a developer's choice of what to narrate is the only thing keeping a value off the line.
 */
class TranslatedNarrationValueSubstitutionTest {

  private static final String RAW = "Charging {amount} to {customerId}";

  @Test
  @DisplayName("ADV-2026-10-30-6: a value that looks like a placeholder is not substituted into")
  void aValueThatLooksLikeAPlaceholderIsRenderedVerbatim() {
    var text =
        render(
            Map.of("es", "Cobrando {amount} a {customerId}"),
            new ParameterEntry("amount", "{customerId}", false),
            new ParameterEntry("customerId", "\"C-1\"", false));

    assertThat(narrationOf(text)).isEqualTo("  Cobrando {customerId} a \"C-1\"");
  }

  @Test
  @DisplayName("ADV-2026-10-30-6: a value the translated template omits stays off the narration")
  void aValueTheTemplateNeverMentionsDoesNotReachTheNarrationLine() {
    var text =
        render(
            Map.of("es", "Cobrando {amount}"),
            new ParameterEntry("amount", "{customerId}", false),
            new ParameterEntry("customerId", "\"4111111111111111\"", false));

    assertThat(narrationOf(text)).isEqualTo("  Cobrando {customerId}");
  }

  @Test
  @DisplayName("ADV-2026-10-30-6: the narration does not depend on parameter capture order")
  void theSameValuesRenderTheSameNarrationWhateverTheParameterOrder() {
    var amount = new ParameterEntry("amount", "{customerId}", false);
    var customerId = new ParameterEntry("customerId", "\"C-1\"", false);
    var variant = Map.of("es", "Cobrando {amount} a {customerId}");

    assertThat(narrationOf(render(variant, amount, customerId)))
        .isEqualTo(narrationOf(render(variant, customerId, amount)));
  }

  private static String render(Map<String, String> variant, ParameterEntry... parameters) {
    var glossary =
        new Glossary(
            1,
            Map.of("billing", new BoundedContext("billing", List.of("com.acme.billing"), null)),
            List.of(
                new GlossaryTerm(
                    "charge",
                    "billing",
                    TermKind.VERB_PHRASE,
                    TermStatus.CURATED,
                    null,
                    Map.of("es", "cobrar"),
                    List.of(),
                    List.of(),
                    LocalDate.of(2026, 8, 11)),
                new GlossaryTerm(
                    RAW,
                    "billing",
                    TermKind.TEMPLATE,
                    TermStatus.CURATED,
                    null,
                    variant,
                    List.of(),
                    List.of(),
                    LocalDate.of(2026, 8, 11))));
    var view = new TraceTranslationView(glossary, className -> "com.acme.billing");
    return view.render(List.of(enterWithTemplate(parameters)), "es");
  }

  private static CanonicalEntry enterWithTemplate(ParameterEntry... parameters) {
    return CanonicalEntry.builder()
        .timestamp("2026-08-14T10:00:00.000Z")
        .level("trace")
        .message("→ PaymentService.charge")
        .traceId("0123456789abcdef0123456789abcdef")
        .spanId("0000000000000001")
        .codeNamespace("PaymentService")
        .codeFunction("charge")
        .ntEntryType("entry")
        .ntEventType("method_enter")
        .ntSchemaVersion("1.1")
        .ntParameters(List.of(parameters))
        .ntNarrationTemplate(RAW)
        .build();
  }

  private static String narrationOf(String text) {
    return text.lines()
        .filter(line -> line.stripLeading().startsWith("Cobrando"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no narration line in:\n" + text));
  }
}
