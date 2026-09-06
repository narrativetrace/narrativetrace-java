/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLClassLoader;
import org.junit.jupiter.api.Test;

class VersionResolverTest {

  @Test
  void resolvesVersionFromPropertiesFile() {
    var version = VersionResolver.resolve();

    assertThat(version).isNotEmpty();
    assertThat(version).doesNotContain("unknown");
  }

  @Test
  void returnsFallbackWhenPropertiesFileMissing() {
    var emptyClassLoader = new URLClassLoader(new java.net.URL[0], null);

    var version = VersionResolver.resolve(emptyClassLoader);

    assertThat(version).isEqualTo(VersionResolver.FALLBACK);
  }

  @Test
  void resolvedVersionMatchesProjectVersion() {
    var version = VersionResolver.resolve();

    assertThat(version).matches("\\d+\\.\\d+\\.\\d+.*");
  }

  @Test
  void resolvesExactlyTheVersionThisPluginPublishes() {
    var published = System.getProperty("narrativetrace.test.publishedVersion");

    assertThat(published).as("the build must pass the published version to the tests").isNotBlank();
    assertThat(VersionResolver.resolve())
        .as(
            "the plugin installs the libraries of its own version, so the two must never"
                + " drift — see gradle.properties")
        .isEqualTo(published);
  }
}
