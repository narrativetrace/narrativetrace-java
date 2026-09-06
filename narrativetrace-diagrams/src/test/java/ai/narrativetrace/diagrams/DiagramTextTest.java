/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.diagrams;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.RenderedValue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiagramTextTest {

  private static RenderedValue objectOf(String typeName) {
    return new RenderedValue.ObjectVal(typeName, Map.of());
  }

  @Test
  void summarizesHomogeneousObjectCollectionAsCountPlusPluralNoun() {
    var list =
        new RenderedValue.ListVal(
            List.of(objectOf("Transfer"), objectOf("Transfer"), objectOf("Transfer")));

    assertThat(DiagramText.returnMessage("[Transfer[...], ...]", list)).isEqualTo("3 transfers");
  }

  @Test
  void usesSingularNounForSingleElementCollection() {
    var list = new RenderedValue.ListVal(List.of(objectOf("Transfer")));

    assertThat(DiagramText.returnMessage("[Transfer[...]]", list)).isEqualTo("1 transfer");
  }

  @Test
  void emptyCollectionReadsZeroItems() {
    var list = new RenderedValue.ListVal(List.of());

    assertThat(DiagramText.returnMessage("[]", list)).isEqualTo("0 items");
  }

  @Test
  void mixedTypeCollectionFallsBackToGenericItemNoun() {
    var list = new RenderedValue.ListVal(List.of(objectOf("Transfer"), objectOf("Refund")));

    assertThat(DiagramText.returnMessage("[...]", list)).isEqualTo("2 items");
  }

  @Test
  void scalarElementCollectionFallsBackToGenericItemNoun() {
    var list =
        new RenderedValue.ListVal(
            List.of(new RenderedValue.LongVal(1), new RenderedValue.LongVal(2)));

    assertThat(DiagramText.returnMessage("[1, 2]", list)).isEqualTo("2 items");
  }

  @Test
  void splitsAMultiWordTypeNameIntoWords() {
    var list =
        new RenderedValue.ListVal(
            List.of(
                objectOf("TemperatureReading"),
                objectOf("TemperatureReading"),
                objectOf("TemperatureReading")));

    // Reported from dogfooding as "3 temperaturereadings" — the space between count and noun was
    // there all along; the word boundary inside the type name was not.
    assertThat(DiagramText.returnMessage("[...]", list)).isEqualTo("3 temperature readings");
  }

  @Test
  void pluralizesOnlyTheLastWordOfAMultiWordNoun() {
    var list = new RenderedValue.ListVal(List.of(objectOf("ColdChainCustody")));

    assertThat(DiagramText.returnMessage("[...]", list)).isEqualTo("1 cold chain custody");
  }

  @Test
  void appliesTheSpellingRulesToTheLastWordOfAMultiWordNoun() {
    var list =
        new RenderedValue.ListVal(
            List.of(objectOf("ShipmentCategory"), objectOf("ShipmentCategory")));

    assertThat(DiagramText.returnMessage("[...]", list)).isEqualTo("2 shipment categories");
  }

  @Test
  void keepsAnAcronymRunTogetherRatherThanSplittingEveryCapital() {
    var list = new RenderedValue.ListVal(List.of(objectOf("HTTPRequest"), objectOf("HTTPRequest")));

    // Splitting on every capital would read "h t t p request"; the boundary is lower-to-upper.
    assertThat(DiagramText.returnMessage("[...]", list)).isEqualTo("2 httprequests");
  }

  @Test
  void pluralizesNounEndingInSWithEs() {
    var list = new RenderedValue.ListVal(List.of(objectOf("Class"), objectOf("Class")));

    assertThat(DiagramText.returnMessage("[...]", list)).isEqualTo("2 classes");
  }

  @Test
  void pluralizesConsonantYNounWithIes() {
    var list = new RenderedValue.ListVal(List.of(objectOf("Category"), objectOf("Category")));

    assertThat(DiagramText.returnMessage("[...]", list)).isEqualTo("2 categories");
  }

  @Test
  void truncatesLongScalarWithEllipsis() {
    var longScalar = "\"" + "x".repeat(120) + "\"";

    var message = DiagramText.returnMessage(longScalar, new RenderedValue.StringVal(longScalar));

    assertThat(message).hasSize(DiagramText.MAX_SCALAR_LENGTH + 1).endsWith("…");
    assertThat(message).startsWith("\"xxxx");
  }

  @Test
  void keepsScalarAtThresholdLengthUnchanged() {
    var atThreshold = "y".repeat(DiagramText.MAX_SCALAR_LENGTH);

    assertThat(DiagramText.returnMessage(atThreshold, new RenderedValue.StringVal(atThreshold)))
        .isEqualTo(atThreshold);
  }

  @Test
  void nullStructuredValueFallsBackToTruncatedScalar() {
    var longScalar = "z".repeat(120);

    var message = DiagramText.returnMessage(longScalar, null);

    assertThat(message).hasSize(DiagramText.MAX_SCALAR_LENGTH + 1).endsWith("…");
  }

  @Test
  void voidCompletionRendersCheckMarkRegardlessOfStructuredValue() {
    assertThat(DiagramText.returnMessage(null, null)).isEqualTo("✓");
  }

  @Test
  void foldsEveryControlCharacterToSpaceNotJustLineFeed() {
    // Covers CR, TAB and a generic C0 control alongside LF — narrowing the fold to '\n' alone would
    // leave these line-injection vectors live.
    var raw = "a\nb\rc\td" + (char) 0 + "e";

    assertThat(DiagramText.message(raw)).isEqualTo("a b c d e");
  }

  @Test
  void preservesTextWithNoControlCharactersUnchanged() {
    assertThat(DiagramText.message("\"order-42\"")).isEqualTo("\"order-42\"");
  }
}
