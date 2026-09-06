/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.spi;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Builds throwaway {@code META-INF/services} declarations for discovery tests.
 *
 * <p>INTENT: Extension tests need control over what is declared, without the declarations leaking
 * into every other test in the module through the real test classpath. The returned loader
 * contributes only the declaration files; provider classes still resolve through the parent loader.
 */
public final class ServiceDeclarations {

  private ServiceDeclarations() {}

  /**
   * Declares the given provider classes for a service type.
   *
   * @param directory scratch directory that becomes the loader's classpath root
   * @param serviceType the extension point being declared
   * @param providers provider classes to declare, in order
   * @return a loader exposing exactly these declarations
   * @throws IOException if the declaration file cannot be written
   */
  public static ClassLoader declaring(Path directory, Class<?> serviceType, Class<?>... providers)
      throws IOException {
    var names = List.of(providers).stream().map(Class::getName).toArray(String[]::new);
    return declaringNames(directory, serviceType, names);
  }

  /**
   * Declares raw provider names, so tests can express missing or malformed entries.
   *
   * @param directory scratch directory that becomes the loader's classpath root
   * @param serviceType the extension point being declared
   * @param providerClassNames literal lines to write into the declaration file
   * @return a loader exposing exactly these declarations
   * @throws IOException if the declaration file cannot be written
   */
  public static ClassLoader declaringNames(
      Path directory, Class<?> serviceType, String... providerClassNames) throws IOException {
    var services = directory.resolve("META-INF/services");
    Files.createDirectories(services);
    Files.write(services.resolve(serviceType.getName()), List.of(providerClassNames));
    return new URLClassLoader(
        new URL[] {directory.toUri().toURL()}, ServiceDeclarations.class.getClassLoader());
  }
}
