/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;

class IdentifierTokenizerPropertyTest {

  private final IdentifierTokenizer tokenizer = new IdentifierTokenizer();

  @Property
  void tokenizeNeverProducesEmptyTokens(
      @ForAll @AlphaChars @StringLength(min = 1, max = 50) String identifier) {
    var tokens = tokenizer.tokenize(identifier);

    assertThat(tokens).isNotEmpty();
    assertThat(tokens).allSatisfy(token -> assertThat(token).isNotEmpty());
  }

  @Property
  void allTokensAreLowercase(
      @ForAll @AlphaChars @StringLength(min = 1, max = 50) String identifier) {
    var tokens = tokenizer.tokenize(identifier);

    assertThat(tokens).allSatisfy(token -> assertThat(token).isEqualTo(token.toLowerCase()));
  }

  @Property
  void tokensJoinBackToLowercaseOriginal(
      @ForAll @AlphaChars @StringLength(min = 1, max = 50) String identifier) {
    var tokens = tokenizer.tokenize(identifier);

    assertThat(String.join("", tokens)).isEqualTo(identifier.toLowerCase());
  }
}
