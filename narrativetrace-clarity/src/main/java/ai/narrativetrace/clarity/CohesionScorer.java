/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import java.util.List;
import java.util.Map;

/** Scores vocabulary consistency within classes based on shared token overlap. */
public final class CohesionScorer {

  private static final double UNKNOWN_ROLE_SCORE = 0.7;
  private static final double BROAD_ROLE_SCORE = 0.9;

  private final IdentifierTokenizer tokenizer = new IdentifierTokenizer();
  private final RoleSuffixDictionary roleSuffixDictionary = new RoleSuffixDictionary();

  public double scoreClass(String className, List<String> methodNames) {
    var tokens = tokenizer.tokenize(className);
    if (tokens.isEmpty()) return UNKNOWN_ROLE_SCORE;

    var lastToken = tokens.get(tokens.size() - 1);
    var expectedVerbs = roleSuffixDictionary.expectedVerbs(lastToken);

    if (expectedVerbs.isEmpty()) {
      var roleResult = roleSuffixDictionary.classify(lastToken);
      if (roleResult.category() == RoleSuffixDictionary.Category.UNKNOWN) {
        return UNKNOWN_ROLE_SCORE;
      }
      return BROAD_ROLE_SCORE;
    }

    if (methodNames.isEmpty()) return UNKNOWN_ROLE_SCORE;

    long aligned = methodNames.stream().filter(m -> isAligned(m, expectedVerbs)).count();

    return (double) aligned / methodNames.size();
  }

  public double scoreTrace(Map<String, List<String>> classMethods) {
    if (classMethods.isEmpty()) return UNKNOWN_ROLE_SCORE;

    return classMethods.entrySet().stream()
        .mapToDouble(e -> scoreClass(e.getKey(), e.getValue()))
        .average()
        .orElse(UNKNOWN_ROLE_SCORE);
  }

  private boolean isAligned(String methodName, List<String> expectedVerbs) {
    var tokens = tokenizer.tokenize(methodName);
    if (tokens.isEmpty()) return false;
    var firstToken = tokens.get(0);
    return expectedVerbs.stream().anyMatch(firstToken::startsWith);
  }
}
