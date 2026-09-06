/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import ai.narrativetrace.core.config.ConfigResolver;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Locates and reads the committed glossary for live translation.
 *
 * <p>INTENT: How live translation finds its glossary without any command-line plumbing. A deployed
 * application carries its glossary on the classpath ({@code glossary.json} at the resource root);
 * the {@code narrativetrace.glossary.path} key — resolved through the standard {@link
 * ConfigResolver} chain (system property, then {@code narrativetrace.properties}) — points to a
 * file instead when the glossary lives outside the artifact (e.g. a committed repo-root {@code
 * glossary.json}). An absent glossary is a normal state (translation simply stays off — the
 * owner-requirement pattern of the vocabulary check); a configured-but-missing override is a
 * configuration error and fails fast.
 */
public final class GlossaryLoader {

  /** Config key pointing at a glossary file outside the classpath. */
  public static final String PATH_KEY = "narrativetrace.glossary.path";

  static final String CLASSPATH_RESOURCE = "glossary.json";

  private GlossaryLoader() {}

  /**
   * Loads the glossary using the context class loader and default config resolution.
   *
   * @return the glossary, or empty when none is present
   */
  public static Optional<Glossary> load() {
    var classLoader = Thread.currentThread().getContextClassLoader();
    return load(new ConfigResolver(classLoader), classLoader);
  }

  /**
   * Loads the glossary: the {@link #PATH_KEY} override first, else the classpath resource.
   *
   * @param resolver config resolution chain deciding whether a path override is set
   * @param classLoader class loader used to locate the {@code glossary.json} resource
   * @return the glossary, or empty when no override is configured and no resource exists
   * @throws IllegalStateException when the configured override points to a missing file
   * @throws UncheckedIOException when a located glossary cannot be read
   */
  public static Optional<Glossary> load(ConfigResolver resolver, ClassLoader classLoader) {
    if (resolver == null) {
      throw new IllegalArgumentException("resolver must not be null");
    }
    if (classLoader == null) {
      throw new IllegalArgumentException("classLoader must not be null");
    }
    var overridePath = resolver.resolve(PATH_KEY, null);
    if (overridePath != null) {
      return Optional.of(readFile(Path.of(overridePath)));
    }
    return readClasspath(classLoader);
  }

  private static Glossary readFile(Path path) {
    if (!Files.isRegularFile(path)) {
      throw new IllegalStateException(PATH_KEY + " points to a missing file: " + path);
    }
    try {
      return new GlossaryJsonReader().read(Files.readString(path));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read glossary at " + path, e);
    }
  }

  private static Optional<Glossary> readClasspath(ClassLoader classLoader) {
    var url = classLoader.getResource(CLASSPATH_RESOURCE);
    if (url == null) {
      return Optional.empty();
    }
    try (var stream = url.openStream()) {
      var json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      return Optional.of(new GlossaryJsonReader().read(json));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read classpath glossary at " + url, e);
    }
  }
}
