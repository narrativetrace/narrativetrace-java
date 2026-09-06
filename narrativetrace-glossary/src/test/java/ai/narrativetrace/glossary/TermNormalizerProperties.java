/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Safety property: normalization converges — camelCase, PascalCase, and snake_case spellings of the
 * same words all produce one normalized phrase (plan section 10).
 */
class TermNormalizerProperties {

  private final TermNormalizer normalizer = new TermNormalizer();

  @Property
  void allSpellingsOfTheSameWordsConvergeToOnePhrase(@ForAll("wordLists") List<String> words) {
    var camel = camelCase(words);
    var pascal = capitalize(camel);
    var snake = String.join("_", words);

    var fromCamel = normalizer.phrase(camel);

    assertThat(normalizer.phrase(pascal)).isEqualTo(fromCamel);
    assertThat(normalizer.phrase(snake)).isEqualTo(fromCamel);
    assertThat(fromCamel).isEqualTo(fromCamel.toLowerCase(java.util.Locale.ROOT));
  }

  @Property
  void normalizationIsIdempotentOnItsOwnOutput(@ForAll("wordLists") List<String> words) {
    var once = normalizer.phrase(camelCase(words));
    var again = normalizer.phrase(once.replace(' ', '_'));

    assertThat(again).isEqualTo(once);
  }

  @Property
  void normalizationIsIdempotentOnArbitraryTokens(@ForAll("tokens") String token) {
    var once = normalizer.phrase(token);

    assertThat(normalizer.phrase(once.replace(' ', '_'))).isEqualTo(once);
  }

  @Provide
  Arbitrary<String> tokens() {
    return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(12);
  }

  @Provide
  Arbitrary<List<String>> wordLists() {
    return Arbitraries.of(
            "account",
            "overdraft",
            "with",
            "payment",
            "plans",
            "entries",
            "status",
            "boxes",
            "customer",
            "charge",
            "insufficient",
            "funds",
            "limit",
            "for",
            "aliases",
            "gases",
            "series",
            "cases",
            "responses",
            "lenses",
            "news",
            "species",
            "always")
        .list()
        .ofMinSize(1)
        .ofMaxSize(4);
  }

  private static String camelCase(List<String> words) {
    return words.get(0)
        + words.subList(1, words.size()).stream()
            .map(TermNormalizerProperties::capitalize)
            .collect(Collectors.joining());
  }

  private static String capitalize(String word) {
    return Character.toUpperCase(word.charAt(0)) + word.substring(1);
  }
}
