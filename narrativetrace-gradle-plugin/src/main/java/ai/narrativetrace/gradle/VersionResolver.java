/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import java.io.IOException;
import java.util.Properties;

final class VersionResolver {

  static final String FALLBACK = "0.0.0-unknown";
  private static final String PROPERTIES_PATH = "narrativetrace-version.properties";

  private VersionResolver() {}

  static String resolve() {
    return resolve(VersionResolver.class.getClassLoader());
  }

  static String resolve(ClassLoader classLoader) {
    try (var stream = classLoader.getResourceAsStream(PROPERTIES_PATH)) {
      if (stream == null) {
        return FALLBACK;
      }
      var props = new Properties();
      props.load(stream);
      return props.getProperty("version", FALLBACK);
    } catch (IOException e) {
      return FALLBACK;
    }
  }
}
