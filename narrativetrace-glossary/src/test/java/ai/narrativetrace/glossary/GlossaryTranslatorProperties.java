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
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Safety properties of phrase translation: with no glossary coverage the phrase passes through
 * byte-identical (never mangled), and a translation is only ever reported complete when the
 * glossary actually covered it.
 */
class GlossaryTranslatorProperties {

  private static final Glossary EMPTY =
      new Glossary(
          1,
          Map.of("billing", new BoundedContext("billing", List.of("com.acme.billing"), null)),
          List.of());

  @Property
  void anUncoveredPhrasePassesThroughByteIdenticalAndIncomplete(@ForAll("phrases") String phrase) {
    var result = new GlossaryTranslator(EMPTY).translate(phrase, "billing", "es");

    assertThat(result.text()).isEqualTo(phrase);
    assertThat(result.complete()).isFalse();
  }

  @Provide
  Arbitrary<String> phrases() {
    var word = Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(10);
    return word.list().ofMinSize(1).ofMaxSize(5).map(words -> String.join(" ", words));
  }
}
