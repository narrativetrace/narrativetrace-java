/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class DictionaryConsistencyPropertyTest {
  private static final Set<String> NON_VERB_ROLE_TOKENS =
      Set.of("of", "from", "to", "with", "on", "new");
  private static final Set<String> NON_ACTION_COLLOCATION_VERBS = Set.of("service");

  private static final Map<String, Set<String>> COLLOCATIONS =
      readStaticMapField(CollocationDictionary.class, "ALL_COLLOCATIONS");
  private static final Map<String, List<String>> ROLE_EXPECTED_VERBS =
      readStaticMapField(RoleSuffixDictionary.class, "EXPECTED_VERBS");
  private static final Map<String, AbbreviationDictionary.Entry> ABBREVIATIONS =
      readStaticMapField(AbbreviationDictionary.class, "ABBREVIATIONS");

  private final CollocationDictionary collocations = new CollocationDictionary();
  private final RoleSuffixDictionary roleSuffixes = new RoleSuffixDictionary();
  private final AbbreviationDictionary abbreviations = new AbbreviationDictionary();
  private final VerbDictionary verbs = new VerbDictionary();

  @Property
  void collocationPairsRemainPreferred(@ForAll("collocationPairs") CollocationPair pair) {
    assertThat(collocations.isPreferred(pair.verb(), pair.noun())).isTrue();
  }

  @Property
  void collocationLookupIsCaseInsensitive(@ForAll("collocationNouns") String noun) {
    var lower = collocations.preferredVerbs(noun.toLowerCase());
    var upper = collocations.preferredVerbs(noun.toUpperCase());
    assertThat(upper).isEqualTo(lower);
    assertThat(lower).isNotEmpty();
  }

  @Property
  void expectedRoleSuffixesAreKnownAndCaseInsensitive(@ForAll("roleSuffixes") String suffix) {
    var result = roleSuffixes.classify(suffix);
    assertThat(result.category()).isNotEqualTo(RoleSuffixDictionary.Category.UNKNOWN);
    assertThat(roleSuffixes.expectedVerbs(suffix.toUpperCase()))
        .isEqualTo(roleSuffixes.expectedVerbs(suffix.toLowerCase()));
  }

  @Property
  void abbreviationsRemainCaseInsensitive(@ForAll("abbreviationKeys") String key) {
    var lower = abbreviations.lookup(key.toLowerCase());
    var upper = abbreviations.lookup(key.toUpperCase());
    assertThat(lower).isNotNull();
    assertThat(upper).isEqualTo(lower);
  }

  @Property
  void collocationVerbsAreKnownByVerbDictionary(@ForAll("collocationPairs") CollocationPair pair) {
    var category = verbs.categorize(pair.verb()).category();
    assertThat(category)
        .as("noun=%s verb=%s", pair.noun(), pair.verb())
        .isNotEqualTo(VerbDictionary.Category.UNKNOWN);
  }

  @Property
  void expectedRoleVerbsAreKnownByVerbDictionary(@ForAll("roleExpectedVerbs") String expectedVerb) {
    var category = verbs.categorize(expectedVerb).category();
    assertThat(category).isNotEqualTo(VerbDictionary.Category.UNKNOWN);
  }

  @Provide
  Arbitrary<CollocationPair> collocationPairs() {
    var pairs = new ArrayList<CollocationPair>();
    COLLOCATIONS.forEach(
        (noun, verbs) ->
            verbs.stream()
                .filter(verb -> !NON_ACTION_COLLOCATION_VERBS.contains(verb))
                .forEach(verb -> pairs.add(new CollocationPair(noun, verb))));
    return Arbitraries.of(pairs);
  }

  @Provide
  Arbitrary<String> collocationNouns() {
    return Arbitraries.of(COLLOCATIONS.keySet());
  }

  @Provide
  Arbitrary<String> roleSuffixes() {
    return Arbitraries.of(ROLE_EXPECTED_VERBS.keySet());
  }

  @Provide
  Arbitrary<String> roleExpectedVerbs() {
    return Arbitraries.of(
        ROLE_EXPECTED_VERBS.values().stream()
            .flatMap(List::stream)
            .map(String::toLowerCase)
            .filter(v -> !NON_VERB_ROLE_TOKENS.contains(v))
            .distinct()
            .toList());
  }

  @Provide
  Arbitrary<String> abbreviationKeys() {
    return Arbitraries.of(ABBREVIATIONS.keySet());
  }

  @SuppressWarnings("unchecked")
  private static <K, V> Map<K, V> readStaticMapField(Class<?> type, String fieldName) {
    try {
      Field field = type.getDeclaredField(fieldName);
      field.setAccessible(true);
      return (Map<K, V>) field.get(null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(
          "Unable to read static field " + type.getSimpleName() + "." + fieldName, e);
    }
  }

  private record CollocationPair(String noun, String verb) {}
}
