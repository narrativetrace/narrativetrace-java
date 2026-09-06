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
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DictionaryDriftGuardTest {

  @Test
  void dictionarySizesStayAboveGuardrails() {
    var abbreviationCount = readStaticMapSize(AbbreviationDictionary.class, "ABBREVIATIONS");
    var collocationNounCount = readStaticMapSize(CollocationDictionary.class, "ALL_COLLOCATIONS");
    var roleExpectationCount = readStaticMapSize(RoleSuffixDictionary.class, "EXPECTED_VERBS");
    var domainVerbCount = readStaticSetSize(VerbDictionary.class, "DOMAIN_VERBS");

    // Keep these guardrails intentionally conservative to catch accidental dictionary shrinkage.
    assertThat(abbreviationCount).isGreaterThanOrEqualTo(170);
    assertThat(collocationNounCount).isGreaterThanOrEqualTo(200);
    assertThat(roleExpectationCount).isGreaterThanOrEqualTo(25);
    assertThat(domainVerbCount).isGreaterThanOrEqualTo(500);
  }

  @Test
  void reportsCurrentDictionaryMetrics() {
    var abbreviationCount = readStaticMapSize(AbbreviationDictionary.class, "ABBREVIATIONS");
    var collocationNounCount = readStaticMapSize(CollocationDictionary.class, "ALL_COLLOCATIONS");
    var roleExpectationCount = readStaticMapSize(RoleSuffixDictionary.class, "EXPECTED_VERBS");
    var domainVerbCount = readStaticSetSize(VerbDictionary.class, "DOMAIN_VERBS");

    var summary =
        "Dictionary metrics: abbreviations=%d collocationNouns=%d roleExpectations=%d domainVerbs=%d"
            .formatted(
                abbreviationCount, collocationNounCount, roleExpectationCount, domainVerbCount);
    assertThat(summary).contains("Dictionary metrics:");
    System.out.println(summary);
  }

  @SuppressWarnings("unchecked")
  private static int readStaticMapSize(Class<?> type, String fieldName) {
    try {
      Field field = type.getDeclaredField(fieldName);
      field.setAccessible(true);
      Object value = field.get(null);
      if (value instanceof Map<?, ?> map) return map.size();
      throw new IllegalStateException(type.getSimpleName() + "." + fieldName + " is not a map");
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(
          "Unable to read static field " + type.getSimpleName() + "." + fieldName, e);
    }
  }

  private static int readStaticSetSize(Class<?> type, String fieldName) {
    try {
      Field field = type.getDeclaredField(fieldName);
      field.setAccessible(true);
      Object value = field.get(null);
      if (value instanceof Set<?> set) return set.size();
      throw new IllegalStateException(type.getSimpleName() + "." + fieldName + " is not a set");
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(
          "Unable to read static field " + type.getSimpleName() + "." + fieldName, e);
    }
  }
}
