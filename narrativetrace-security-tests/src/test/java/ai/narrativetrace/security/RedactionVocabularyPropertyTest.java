/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.render.ValueRenderer;
import ai.narrativetrace.security.corpus.HostileCorpus;
import ai.narrativetrace.security.corpus.RedactionCase;
import ai.narrativetrace.security.oracle.Emitters;
import ai.narrativetrace.security.oracle.Oracles;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The redaction oracle over the shared sensitive-vocabulary corpus, in every emitter the product
 * ships.
 *
 * <p>INTENT: {@code RedactionPolicy} answering "yes" is not protection — protection is the byte
 * never reaching an output. This drives every row of {@code redaction.json} through the value
 * renderer and every downstream emitter, and asserts the direction the row declares: a canary
 * behind a sensitive name, or a national-id value, appears in no byte of any output; a canary
 * behind a near-miss name stays readable.
 *
 * <p><b>@llmNote</b> The visible half carries the same weight as the hidden half, on purpose. A
 * matcher that redacted everything would satisfy a suite that only ever asserted absence, and the
 * result is a default that teams switch off — which leaks every field rather than one.
 *
 * <p><b>@edgeCase</b> Absence is asserted across every emitter; presence only across the two value
 * renderers. Downstream emitters legitimately escape and re-encode what they are given, so {@code
 * contains} there would be asserting the escaping rule rather than the redaction rule. Absence has
 * no such asymmetry: no escaping can make a hidden value reappear.
 *
 * <p><b>@edgeCase</b> Name rows are rendered as a one-entry {@code Map}, because these names are
 * data rather than compile-time identifiers — the corpus carries two spellings of the same Spanish
 * word that differ only in Unicode normalization form, and no record can declare both.
 */
class RedactionVocabularyPropertyTest {

  private final ValueRenderer renderer = new ValueRenderer();

  static List<RedactionCase> corpus() {
    return HostileCorpus.redactions();
  }

  static List<RedactionCase> redactedNames() {
    return corpus().stream()
        .filter(RedactionCase::isName)
        .filter(RedactionCase::expectsRedaction)
        .toList();
  }

  @ParameterizedTest
  @MethodSource("corpus")
  void everyCorpusRowGoesTheWayItDeclares(RedactionCase corpusCase) {
    var outputs =
        Oracles.withinBudget(
            "every output for " + corpusCase.id(), () -> everyOutput(corpusCase.payload()));

    if (corpusCase.expectsRedaction()) {
      assertHiddenEverywhere(corpusCase, outputs);
    } else {
      assertReadableInTheValueRenderers(corpusCase, outputs);
    }
    Oracles.boundedSize(outputs);
  }

  private static void assertHiddenEverywhere(
      RedactionCase corpusCase, Map<String, String> outputs) {
    outputs.forEach(
        (emitter, output) ->
            assertThat(output)
                .as("%s (%s) must not reach %s", corpusCase.id(), corpusCase.description(), emitter)
                .doesNotContain(corpusCase.secret()));
  }

  private static void assertReadableInTheValueRenderers(
      RedactionCase corpusCase, Map<String, String> outputs) {
    for (var emitter : List.of("renderer:value-flat", "renderer:value-structured")) {
      assertThat(outputs.get(emitter))
          .as(
              "%s (%s) must stay readable in %s",
              corpusCase.id(), corpusCase.description(), emitter)
          .contains(corpusCase.secret());
    }
  }

  /**
   * A redacted field must leave the marker behind, not silence. Silence satisfies containment too,
   * and a reader cannot tell "hidden" from "never captured".
   */
  @ParameterizedTest
  @MethodSource("redactedNames")
  void aRedactedFieldShowsTheMarkerRatherThanNothing(RedactionCase corpusCase) {
    var rendered = renderer.render(corpusCase.payload());

    assertThat(rendered)
        .as("%s must say it hid something", corpusCase.id())
        .contains("[REDACTED]")
        .doesNotContain(corpusCase.secret());
  }

  /**
   * The bug class rather than the instance: every leak found so far lived in a wrapper — {@code
   * Optional}, {@code AtomicReference}, a standalone {@code Map.Entry}. A sensitive name three
   * containers deep is the same secret.
   */
  @ParameterizedTest
  @MethodSource("redactedNames")
  void aSensitiveNameIsStillHiddenSeveralContainersDeep(RedactionCase corpusCase) {
    var nested = Map.of("outer", List.of(Optional.of(corpusCase.payload())));

    assertHiddenEverywhere(corpusCase, everyOutput(nested));
  }

  @Test
  void theCorpusCoversBothDirectionsAndBothAxes() {
    var rows = corpus();

    assertThat(rows).hasSizeGreaterThan(60);
    assertThat(rows).anyMatch(RedactionCase::isName).anyMatch(row -> !row.isName());
    assertThat(rows)
        .anyMatch(RedactionCase::expectsRedaction)
        .anyMatch(row -> !row.expectsRedaction());
    assertThat(rows.stream().filter(row -> !row.expectsRedaction()).count())
        .as("the false-positive half is what keeps the default switched on")
        .isGreaterThan(25);
  }

  private Map<String, String> everyOutput(Object graph) {
    var flat = renderer.render(graph);
    var outputs = new LinkedHashMap<String, String>();
    outputs.put("renderer:value-flat", flat);
    outputs.put("renderer:value-structured", String.valueOf(renderer.renderStructured(graph)));
    outputs.putAll(Emitters.everyOutput(Emitters.treeOf(flat, flat)));
    return outputs;
  }
}
