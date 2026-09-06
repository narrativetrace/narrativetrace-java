/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.core.config.ConfigResolver;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GlossaryLoaderTest {

  @TempDir Path tempDir;

  private static Glossary glossaryWithTerm(String termText) {
    return new Glossary(
        1,
        Map.of("billing", new BoundedContext("billing", List.of("com.acme.billing"), null)),
        List.of(
            new GlossaryTerm(
                termText,
                "billing",
                TermKind.WORD,
                TermStatus.CURATED,
                null,
                Map.of("es", "traducción"),
                List.of(),
                List.of(),
                LocalDate.of(2026, 8, 15))));
  }

  private static String json(Glossary glossary) {
    return new GlossaryJsonWriter().write(glossary);
  }

  private URLClassLoader classLoaderOver(Path dir) throws Exception {
    return new URLClassLoader(new URL[] {dir.toUri().toURL()}, null);
  }

  @Test
  void loadsTheClasspathGlossaryResource() throws Exception {
    Files.writeString(tempDir.resolve("glossary.json"), json(glossaryWithTerm("charge")));
    try (var cl = classLoaderOver(tempDir)) {
      var loaded = GlossaryLoader.load(new ConfigResolver(cl), cl);

      assertThat(loaded).contains(glossaryWithTerm("charge"));
    }
  }

  @Test
  void returnsEmptyWhenNoGlossaryIsPresentAnywhere() throws Exception {
    try (var cl = classLoaderOver(tempDir)) {
      assertThat(GlossaryLoader.load(new ConfigResolver(cl), cl)).isEmpty();
    }
  }

  @Test
  void configuredPathOverrideWinsOverTheClasspathResource() throws Exception {
    Files.writeString(tempDir.resolve("glossary.json"), json(glossaryWithTerm("classpath")));
    var override = tempDir.resolve("committed-glossary.json");
    Files.writeString(override, json(glossaryWithTerm("override")));
    Files.writeString(
        tempDir.resolve("narrativetrace.properties"),
        "narrativetrace.glossary.path=" + override.toString().replace("\\", "\\\\") + "\n");
    try (var cl = classLoaderOver(tempDir)) {
      var loaded = GlossaryLoader.load(new ConfigResolver(cl), cl);

      assertThat(loaded).contains(glossaryWithTerm("override"));
    }
  }

  @Test
  void anOverridePointingToAMissingFileFailsFast() throws Exception {
    Files.writeString(
        tempDir.resolve("narrativetrace.properties"),
        "narrativetrace.glossary.path=" + tempDir.resolve("nowhere.json") + "\n");
    try (var cl = classLoaderOver(tempDir)) {
      assertThatThrownBy(() -> GlossaryLoader.load(new ConfigResolver(cl), cl))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("narrativetrace.glossary.path");
    }
  }

  @Test
  void invalidGlossaryJsonPropagatesAsAnError() throws Exception {
    Files.writeString(tempDir.resolve("glossary.json"), "{ not json");
    try (var cl = classLoaderOver(tempDir)) {
      assertThatThrownBy(() -> GlossaryLoader.load(new ConfigResolver(cl), cl))
          .isInstanceOf(RuntimeException.class);
    }
  }

  @Test
  void noArgLoadUsesTheContextClassLoader() throws Exception {
    Files.writeString(tempDir.resolve("glossary.json"), json(glossaryWithTerm("context")));
    var previous = Thread.currentThread().getContextClassLoader();
    try (var cl = classLoaderOver(tempDir)) {
      Thread.currentThread().setContextClassLoader(cl);

      assertThat(GlossaryLoader.load()).contains(glossaryWithTerm("context"));
    } finally {
      Thread.currentThread().setContextClassLoader(previous);
    }
  }

  @Test
  void rejectsNullArguments() {
    assertThatThrownBy(() -> GlossaryLoader.load(null, getClass().getClassLoader()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> GlossaryLoader.load(new ConfigResolver(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
