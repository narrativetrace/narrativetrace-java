/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import java.util.regex.Pattern;

final class CamelCaseSplitter {

  private static final Pattern CAMEL_CASE_SPLIT = Pattern.compile("(?<=[a-z])(?=[A-Z])");

  private CamelCaseSplitter() {}

  static String[] split(String input) {
    return CAMEL_CASE_SPLIT.split(input);
  }

  static String toPhrase(String input) {
    return String.join(" ", split(input)).toLowerCase();
  }
}
