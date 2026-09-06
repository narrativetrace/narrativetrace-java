/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdentifierTokenizerTest {

  private final IdentifierTokenizer tokenizer = new IdentifierTokenizer();

  @Test
  void splitsSimpleCamelCase() {
    assertThat(tokenizer.tokenize("calculateTotal")).containsExactly("calculate", "total");
  }

  @Test
  void splitsAbbreviationBoundary() {
    assertThat(tokenizer.tokenize("HTTPSConnection")).containsExactly("https", "connection");
  }

  @Test
  void splitsAlphaDigitBoundary() {
    assertThat(tokenizer.tokenize("name2")).containsExactly("name", "2");
  }

  @Test
  void splitsDigitAlphaBoundary() {
    assertThat(tokenizer.tokenize("2name")).containsExactly("2", "name");
  }

  @Test
  void splitsSnakeCase() {
    assertThat(tokenizer.tokenize("parse_xml_document"))
        .containsExactly("parse", "xml", "document");
  }

  @Test
  void handlesSingleWord() {
    assertThat(tokenizer.tokenize("login")).containsExactly("login");
  }

  @Test
  void handlesMixedAbbreviations() {
    assertThat(tokenizer.tokenize("parseXMLDocument")).containsExactly("parse", "xml", "document");
  }

  @Test
  void normalizesToLowercase() {
    assertThat(tokenizer.tokenize("OrderService")).containsExactly("order", "service");
  }

  @Test
  void emptyInputReturnsEmptyList() {
    assertThat(tokenizer.tokenize("")).isEmpty();
  }

  @Test
  void leadingUnderscoreFiltersEmptyTokens() {
    // "_foo" splits on _ → ["", "foo"] → filter removes "" → ["foo"]
    assertThat(tokenizer.tokenize("_foo")).containsExactly("foo");
  }

  @Test
  void whitespaceSeparatesWordsJustAsUnderscoreDoes() {
    assertThat(tokenizer.tokenize("parse xml document"))
        .containsExactly("parse", "xml", "document");
    assertThat(tokenizer.tokenize("parse\txml")).containsExactly("parse", "xml");
    assertThat(tokenizer.tokenize("parse\nxml")).containsExactly("parse", "xml");
  }

  @Test
  void whitespaceIsNeverPartOfAToken() {
    // The defect this pins: "_ " split on "_" alone leaves " " behind, and a whitespace-only
    // token is a word to every caller downstream — it reaches the glossary as a term.
    assertThat(tokenizer.tokenize("a_ ")).containsExactly("a");
    assertThat(tokenizer.tokenize("_ a")).containsExactly("a");
    assertThat(tokenizer.tokenize(" spaced ")).containsExactly("spaced");
  }

  @Test
  void anIdentifierOfSeparatorsAloneHasNoWords() {
    // Not the same as empty input: these are non-blank strings that carry no readable word,
    // so the tokenizer must answer "no words" rather than hand back whitespace.
    assertThat(tokenizer.tokenize("_ ")).isEmpty();
    assertThat(tokenizer.tokenize(" _")).isEmpty();
    assertThat(tokenizer.tokenize("_\n")).isEmpty();
    assertThat(tokenizer.tokenize("__ ")).isEmpty();
    assertThat(tokenizer.tokenize("_ _")).isEmpty();
  }
}
