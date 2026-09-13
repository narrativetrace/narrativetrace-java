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

  @Test
  void identifierOfNullIsEmpty() {
    assertThat(DiagramText.identifier(null)).isEmpty();
  }

  @Test
  void identifierOfBlankTextStandsInAsUnnamed() {
    // The empty string, and text that folds to nothing but spaces, both read as absent rather
    // than as a real, blank name — the same finding the class doc records for `participant `.
    assertThat(DiagramText.identifier("")).isEqualTo("<unnamed>");
    assertThat(DiagramText.identifier("   ")).isEqualTo("<unnamed>");
  }

  @Test
  void identifierDedupesADoubledPercentThatWouldOpenAMermaidComment() {
    // "%%" opens a Mermaid comment; a lone "%" is ordinary text and must survive.
    assertThat(DiagramText.identifier("100%% off")).isEqualTo("100% off");
    assertThat(DiagramText.identifier("50% off")).isEqualTo("50% off");
  }

  @Test
  void identifierTruncatesTextLongerThanTheIdentifierCap() {
    var tooLong = "x".repeat(DiagramText.MAX_IDENTIFIER_LENGTH + 30);

    var result = DiagramText.identifier(tooLong);

    assertThat(result).hasSize(DiagramText.MAX_IDENTIFIER_LENGTH + 1).endsWith("…");
  }

  @Test
  void identifierKeepsTextAtTheCapLengthUnchanged() {
    var atCap = "y".repeat(DiagramText.MAX_IDENTIFIER_LENGTH);

    assertThat(DiagramText.identifier(atCap)).isEqualTo(atCap);
  }

  @Test
  void aliasTokenOfNullFallsBackToP() {
    assertThat(DiagramText.aliasToken(null)).isEqualTo("P");
  }

  @Test
  void aliasTokenSuffixesAClassNameThatIsABareMermaidReservedWord() {
    // Before this fix, a class named "end" yielded the alias "end" unchanged — a bare token
    // Mermaid's grammar reserves for closing a loop/alt/opt/rect/critical/box block, which the
    // parser rejects rather than renders.
    assertThat(DiagramText.aliasToken("end")).isEqualTo("end_");
    assertThat(DiagramText.aliasToken("participant")).isEqualTo("participant_");
    assertThat(DiagramText.aliasToken("loop")).isEqualTo("loop_");
    assertThat(DiagramText.aliasToken("note")).isEqualTo("note_");
  }

  @Test
  void aliasTokenReservedWordMatchIsCaseInsensitive() {
    // Mermaid's sequenceDiagram.jison declares "%options case-insensitive" for its whole lexer.
    assertThat(DiagramText.aliasToken("END")).isEqualTo("END_");
    assertThat(DiagramText.aliasToken("End")).isEqualTo("End_");
  }

  @Test
  void aliasTokenLeavesAnOrdinaryWordThatMerelyContainsAReservedWordAlone() {
    // A word boundary check would be the wrong fix here: the whole token must equal the reserved
    // word, not merely contain it as a substring.
    assertThat(DiagramText.aliasToken("endpoint")).isEqualTo("endpoint");
    assertThat(DiagramText.aliasToken("noteworthy")).isEqualTo("noteworthy");
  }

  @Test
  void plainModeTokenQuotesABareMermaidReservedWord() {
    // Before this fix (quoteIfNeeded, the function plain mode used): "end" returned unchanged — no
    // ". - : < > " space" character to trigger quoting — a bare token both Mermaid's participant
    // declaration and its arrow lines reserve for closing a loop/alt/opt/rect/critical/box block.
    assertThat(DiagramText.plainModeToken("end")).isEqualTo("\"end\"");
    assertThat(DiagramText.plainModeToken("participant")).isEqualTo("\"participant\"");
    assertThat(DiagramText.plainModeToken("loop")).isEqualTo("\"loop\"");
    assertThat(DiagramText.plainModeToken("note")).isEqualTo("\"note\"");
    assertThat(DiagramText.plainModeToken("title")).isEqualTo("\"title\"");
  }

  @Test
  void plainModeTokenQuotesABarePlantUmlOnlyReservedWord() {
    // "boundary" is not a Mermaid sequence-diagram keyword, but it is a PlantUML participant-type
    // keyword (plantuml.com/sequence-diagram) — plainModeToken is shared by both grammars and
    // applies the union of their hazards, the same design already documented for #identifier.
    assertThat(DiagramText.plainModeToken("boundary")).isEqualTo("\"boundary\"");
    assertThat(DiagramText.plainModeToken("skinparam")).isEqualTo("\"skinparam\"");
  }

  @Test
  void plainModeTokenReservedWordMatchIsCaseInsensitive() {
    assertThat(DiagramText.plainModeToken("END")).isEqualTo("\"END\"");
    assertThat(DiagramText.plainModeToken("End")).isEqualTo("\"End\"");
  }

  @Test
  void plainModeTokenLeavesAnOrdinaryWordThatMerelyContainsAReservedWordAlone() {
    // Same word-boundary correctness as aliasToken: the whole identifier must equal the reserved
    // word, not merely contain it as a substring.
    assertThat(DiagramText.plainModeToken("endpoint")).isEqualTo("endpoint");
    assertThat(DiagramText.plainModeToken("noteworthy")).isEqualTo("noteworthy");
  }

  @Test
  void plainModeTokenStillQuotesOnASpecialCharacterUnrelatedToReservedWords() {
    // Regression guard: the pre-existing trigger (". - : < > " space") must survive the new check.
    assertThat(DiagramText.plainModeToken("com.example.OrderService"))
        .isEqualTo("\"com.example.OrderService\"");
    assertThat(DiagramText.plainModeToken("OrderService")).isEqualTo("OrderService");
  }

  @Test
  void quoteIfNeededDeliberatelyLeavesABareReservedWordUnquoted() {
    // quoteIfNeeded is also used for Mermaid alias mode's "as DisplayName" text, which is never
    // itself a bare grammar token — MermaidSequenceDiagramRendererTest pins that a reserved-word
    // class name keeps its unescaped display name there. plainModeToken (above) is the function
    // for an actual participant/arrow token; quoteIfNeeded must NOT gain the reserved-word check.
    assertThat(DiagramText.quoteIfNeeded("end")).isEqualTo("end");
  }
}
