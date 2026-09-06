/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.config;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Resolves configuration values from JVM system properties and a classpath properties file.
 *
 * <p>The lookup order is: matching system property first, then {@code narrativetrace.properties},
 * then the supplied default. Exactly one classpath properties file is allowed; multiple matches
 * trigger {@link DuplicateConfigurationException}.
 */
public final class ConfigResolver {

  private static final String PROPERTIES_FILE = "narrativetrace.properties";

  private final Properties fileProperties;

  /** Creates a resolver using the current thread context class loader. */
  public ConfigResolver() {
    this(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Creates a resolver using the given class loader.
   *
   * @param classLoader class loader used to locate {@code narrativetrace.properties}
   */
  public ConfigResolver(ClassLoader classLoader) {
    this.fileProperties = loadFromClasspath(classLoader);
  }

  /**
   * Resolves one configuration key.
   *
   * @param key the property name
   * @param defaultValue fallback used when no system or file value is present
   * @return the resolved value
   */
  public String resolve(String key, String defaultValue) {
    var systemValue = System.getProperty(key);
    if (systemValue != null) {
      return systemValue;
    }
    return fileProperties.getProperty(key, defaultValue);
  }

  private static Properties loadFromClasspath(ClassLoader classLoader) {
    var props = new Properties();
    if (classLoader == null) {
      return props;
    }
    var locations = findAllLocations(classLoader);
    if (locations.isEmpty()) {
      return props;
    }
    if (locations.size() > 1) {
      throw new DuplicateConfigurationException(
          "Found multiple " + PROPERTIES_FILE + " files on classpath: " + locations);
    }
    try (var stream = locations.get(0).openStream()) {
      props.load(stream);
    } catch (IOException e) {
      // Silently fall back to defaults — file unreadable
    }
    return props;
  }

  private static List<URL> findAllLocations(ClassLoader classLoader) {
    try {
      return Collections.list(classLoader.getResources(PROPERTIES_FILE));
    } catch (IOException e) {
      return List.of();
    }
  }
}
