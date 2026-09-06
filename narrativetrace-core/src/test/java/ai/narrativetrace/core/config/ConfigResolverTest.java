/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import org.junit.jupiter.api.Test;

class ConfigResolverTest {

  @Test
  void returnsDefaultWhenNoFileAndNoSystemProperty() {
    var resolver = new ConfigResolver();

    var value = resolver.resolve("narrativetrace.level", "DETAIL");

    assertThat(value).isEqualTo("DETAIL");
  }

  @Test
  void readsValueFromClasspathPropertiesFile(@org.junit.jupiter.api.io.TempDir Path tempDir)
      throws Exception {
    Files.writeString(
        tempDir.resolve("narrativetrace.properties"), "narrativetrace.level=NARRATIVE\n");
    var classLoader = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null);
    var resolver = new ConfigResolver(classLoader);

    var value = resolver.resolve("narrativetrace.level", "DETAIL");

    assertThat(value).isEqualTo("NARRATIVE");
  }

  @Test
  void systemPropertyOverridesFileValue(@org.junit.jupiter.api.io.TempDir Path tempDir)
      throws Exception {
    Files.writeString(
        tempDir.resolve("narrativetrace.properties"), "narrativetrace.level=NARRATIVE\n");
    var classLoader = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null);
    var resolver = new ConfigResolver(classLoader);
    System.setProperty("narrativetrace.level", "ERRORS");
    try {
      var value = resolver.resolve("narrativetrace.level", "DETAIL");

      assertThat(value).isEqualTo("ERRORS");
    } finally {
      System.clearProperty("narrativetrace.level");
    }
  }

  @Test
  void returnsDefaultWhenClassLoaderIsNull() {
    var resolver = new ConfigResolver(null);

    var value = resolver.resolve("narrativetrace.level", "DETAIL");

    assertThat(value).isEqualTo("DETAIL");
  }

  @Test
  void throwsWhenMultiplePropertiesFilesOnClasspath(@org.junit.jupiter.api.io.TempDir Path tempDir)
      throws Exception {
    var dir1 = tempDir.resolve("dir1");
    var dir2 = tempDir.resolve("dir2");
    Files.createDirectories(dir1);
    Files.createDirectories(dir2);
    Files.writeString(
        dir1.resolve("narrativetrace.properties"), "narrativetrace.level=NARRATIVE\n");
    Files.writeString(dir2.resolve("narrativetrace.properties"), "narrativetrace.level=DETAIL\n");
    var classLoader =
        new URLClassLoader(new URL[] {dir1.toUri().toURL(), dir2.toUri().toURL()}, null);

    assertThatThrownBy(() -> new ConfigResolver(classLoader))
        .isInstanceOf(DuplicateConfigurationException.class)
        .hasMessageContaining("dir1")
        .hasMessageContaining("dir2");
  }

  @Test
  void returnsDefaultWhenGetResourcesThrowsIOException() {
    var brokenClassLoader =
        new ClassLoader() {
          @Override
          public Enumeration<URL> getResources(String name) throws IOException {
            throw new IOException("simulated");
          }
        };
    var resolver = new ConfigResolver(brokenClassLoader);

    assertThat(resolver.resolve("narrativetrace.level", "DETAIL")).isEqualTo("DETAIL");
  }

  @Test
  void returnsDefaultWhenPropertiesFileIsUnreadable(@org.junit.jupiter.api.io.TempDir Path tempDir)
      throws Exception {
    // Create a URL pointing to a directory (opening it as stream will fail)
    var dir = tempDir.resolve("sub");
    Files.createDirectories(dir);
    // Write a valid file first, then wrap the URL to point to a bad stream
    Files.writeString(dir.resolve("narrativetrace.properties"), "narrativetrace.level=NARRATIVE\n");
    var goodUrl = dir.resolve("narrativetrace.properties").toUri().toURL();
    var badUrl =
        new URL(
            goodUrl.getProtocol(),
            goodUrl.getHost(),
            goodUrl.getPort(),
            goodUrl.getFile() + ".nonexistent");
    var classLoader =
        new ClassLoader() {
          @Override
          public Enumeration<URL> getResources(String name) {
            return Collections.enumeration(java.util.List.of(badUrl));
          }
        };
    var resolver = new ConfigResolver(classLoader);

    assertThat(resolver.resolve("narrativetrace.level", "DETAIL")).isEqualTo("DETAIL");
  }
}
