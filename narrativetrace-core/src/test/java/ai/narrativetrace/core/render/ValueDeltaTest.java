/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.RenderedValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The delta helper on its own: which pairs of structured values it can express as a one-line field
 * diff, and which it refuses so the caller keeps the full flat render.
 */
class ValueDeltaTest {

  private static RenderedValue.ObjectVal expense(String typeName, double amount) {
    var fields = new LinkedHashMap<String, RenderedValue>();
    fields.put("description", new RenderedValue.StringVal("Dinner"));
    fields.put("amount", new RenderedValue.DoubleVal(amount));
    return new RenderedValue.ObjectVal(typeName, fields);
  }

  @Test
  void aScalarFieldChangeBecomesAOneLineDiff() {
    assertThat(ValueDelta.between(expense("Expense", 100.0), expense("Expense", 92.0)))
        .isEqualTo("{amount: 100.0→92.0}");
  }

  @Test
  void anUnchangedPairHasNoDelta() {
    assertThat(ValueDelta.between(expense("Expense", 100.0), expense("Expense", 100.0))).isNull();
  }

  @Test
  void aDifferentTypeNameHasNoDelta() {
    assertThat(ValueDelta.between(expense("Expense", 100.0), expense("Refund", 92.0))).isNull();
  }

  @Test
  void aDifferentFieldSetHasNoDelta() {
    var extra =
        new RenderedValue.ObjectVal(
            "Expense", Map.of("description", new RenderedValue.StringVal("Dinner")));

    assertThat(ValueDelta.between(expense("Expense", 100.0), extra)).isNull();
  }

  @Test
  void aScalarComparedAgainstAStructuredValueHasNoDelta() {
    var listed = new LinkedHashMap<String, RenderedValue>();
    listed.put("description", new RenderedValue.StringVal("Dinner"));
    listed.put("amount", new RenderedValue.ListVal(List.of(new RenderedValue.LongVal(1))));

    assertThat(
            ValueDelta.between(
                expense("Expense", 100.0), new RenderedValue.ObjectVal("Expense", listed)))
        .isNull();
  }

  @Test
  void aNonObjectValueIsNeverOneSideOfADelta() {
    var scalar = new RenderedValue.StringVal("Dinner");

    assertThat(ValueDelta.between(scalar, expense("Expense", 92.0))).isNull();
    assertThat(ValueDelta.between(expense("Expense", 100.0), scalar)).isNull();
  }

  @Test
  void aNullSideIsNeverADelta() {
    assertThat(ValueDelta.between(null, expense("Expense", 92.0))).isNull();
    assertThat(ValueDelta.between(expense("Expense", 100.0), null)).isNull();
  }

  @Test
  void anAddedFieldIsNeverSilentlyDroppedFromTheDiff() {
    var richer = new LinkedHashMap<String, RenderedValue>();
    richer.put("description", new RenderedValue.StringVal("Dinner"));
    richer.put("amount", new RenderedValue.DoubleVal(92.0));
    richer.put("currency", new RenderedValue.StringVal("EUR"));

    assertThat(
            ValueDelta.between(
                expense("Expense", 100.0), new RenderedValue.ObjectVal("Expense", richer)))
        .isNull();
  }

  private static RenderedValue.ObjectVal note(String body) {
    var fields = new LinkedHashMap<String, RenderedValue>();
    fields.put("title", new RenderedValue.StringVal("Standup"));
    fields.put("body", new RenderedValue.StringVal(body));
    return new RenderedValue.ObjectVal("Note", fields);
  }

  @Test
  void aStringExactlyAtTheScalarCapIsNotElided() {
    var sixty = "x".repeat(60);

    assertThat(ValueDelta.between(note("before"), note(sixty)))
        .isEqualTo("{body: \"before\"→\"" + sixty + "\"}");
  }

  @Test
  void aStringOneCharacterPastTheScalarCapIsElided() {
    assertThat(ValueDelta.between(note("before"), note("x".repeat(61))))
        .isEqualTo("{body: \"before\"→\"" + "x".repeat(60) + "…\"}");
  }
}
