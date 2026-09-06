/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NarrativeTraceAgentTest {

  @Test
  void premainLoadsSuccessfully() {
    assertThatNoException()
        .isThrownBy(() -> NarrativeTraceAgent.premain("packages=ai.narrativetrace.test", null));
  }

  @Test
  void premainWithInstrumentationRegistersTransformer() {
    var registered = new ArrayList<ClassFileTransformer>();
    Instrumentation inst =
        new StubInstrumentation() {
          @Override
          public void addTransformer(ClassFileTransformer transformer) {
            registered.add(transformer);
          }
        };

    NarrativeTraceAgent.premain("packages=ai.narrativetrace.test", inst);

    assertThat(registered).hasSize(1);
    assertThat(registered.get(0)).isInstanceOf(NarrativeClassFileTransformer.class);
  }

  @Test
  void premainAppendsConfiguredLoggingJarsToTheSystemClassLoader(@TempDir Path dir)
      throws Exception {
    var jar = writeJar(dir.resolve("slf4j-provider.jar"));
    var appended = new ArrayList<String>();
    Instrumentation inst =
        new StubInstrumentation() {
          @Override
          public void addTransformer(ClassFileTransformer transformer) {}

          @Override
          public void appendToSystemClassLoaderSearch(JarFile jarFile) {
            appended.add(jarFile.getName());
          }
        };

    NarrativeTraceAgent.premain("packages=ai.narrativetrace.test,loggingJars=" + jar, inst);

    assertThat(appended).containsExactly(jar.toString());
  }

  @Test
  void premainRejectsALoggingJarsEntryThatIsNotAReadableJar(@TempDir Path dir) throws Exception {
    var notAJar = Files.writeString(dir.resolve("provider.jar"), "this is not a jar");

    assertThatThrownBy(
            () ->
                NarrativeTraceAgent.premain(
                    "packages=ai.narrativetrace.test,loggingJars=" + notAJar,
                    new StubInstrumentation() {
                      @Override
                      public void addTransformer(ClassFileTransformer transformer) {}
                    }))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("not a readable jar")
        .hasMessageContaining(notAJar.toString());
  }

  private static Path writeJar(Path path) throws Exception {
    try (var out = new JarOutputStream(Files.newOutputStream(path), new Manifest())) {
      out.putNextEntry(new JarEntry("marker.txt"));
      out.write("provider".getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
    return path;
  }
}
