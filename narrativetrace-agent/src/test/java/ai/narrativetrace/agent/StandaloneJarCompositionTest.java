/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;

/**
 * Composition contract of the {@code -standalone} classifier jar: the single-file artifact for
 * {@code -javaagent} attach on hosts with no build tool (legacy servers, plain {@code java} apps).
 *
 * <p>Must bundle: agent classes, relocated ASM, {@code narrativetrace-core} (injected bytecode and
 * {@code AgentRuntime} need it and nothing resolves the POM at attach time), the {@code
 * narrativetrace-slf4j} listener, and {@code slf4j-api}. Must NOT bundle any SLF4J provider — the
 * host's logging setup is unknown, the binding is always the user's choice — and must not carry a
 * {@code module-info.class} inherited from bundled dependencies.
 *
 * <p>The jar under test is supplied by the build via the {@code narrativetrace.test.standaloneJar}
 * system property.
 */
class StandaloneJarCompositionTest {

  private String standaloneJarPath() {
    var jarPath = System.getProperty("narrativetrace.test.standaloneJar");
    assertThat(jarPath).as("narrativetrace.test.standaloneJar system property").isNotNull();
    return jarPath;
  }

  @Test
  void bundlesAgentCoreSlf4jListenerAndApi() throws Exception {
    try (var jar = new JarFile(standaloneJarPath())) {
      List<String> entries = jar.stream().map(ZipEntry::getName).toList();

      assertThat(entries).contains("ai/narrativetrace/agent/NarrativeTraceAgent.class");
      assertThat(entries)
          .as("core must be bundled — nothing resolves the POM at -javaagent attach time")
          .contains("ai/narrativetrace/core/context/NarrativeContext.class");
      assertThat(entries)
          .as("the api jar must be bundled too, or the instrumented bytecode links to nothing")
          .contains("ai/narrativetrace/api/annotation/Narrated.class")
          .contains("ai/narrativetrace/api/event/TraceEvent.class");
      assertThat(entries)
          .as("the SLF4J listener must be bundled so a user-supplied binding just works")
          .contains("ai/narrativetrace/slf4j/Slf4jTraceEventListener.class");
      assertThat(entries)
          .as("slf4j-api must be bundled un-relocated for listener linkage on bare hosts")
          .contains("org/slf4j/Logger.class");
      assertThat(entries)
          .as("ASM must be bundled under the relocated internal package")
          .anyMatch(name -> name.startsWith("ai/narrativetrace/agent/internal/asm/"));
      assertThat(entries)
          .as("original ASM packages must not leak into the jar")
          .noneMatch(name -> name.startsWith("org/objectweb/asm/"));
    }
  }

  @Test
  void bundlesNoSlf4jProviderAndNoModuleInfo() throws Exception {
    try (var jar = new JarFile(standaloneJarPath())) {
      List<String> entries = jar.stream().map(ZipEntry::getName).toList();

      assertThat(entries)
          .as("no SLF4J provider may be bundled — the binding is always the user's")
          .noneMatch(
              name ->
                  name.startsWith("org/slf4j/simple/")
                      || name.startsWith("ch/qos/logback/")
                      || name.startsWith("META-INF/services/org.slf4j.spi.SLF4JServiceProvider"));
      assertThat(entries)
          .as("bundled dependencies' module descriptors must be stripped")
          .noneMatch(name -> name.endsWith("module-info.class"));
    }
  }

  @Test
  void declaresPremainManifest() throws Exception {
    try (var jar = new JarFile(standaloneJarPath())) {
      var attributes = jar.getManifest().getMainAttributes();
      assertThat(attributes.getValue("Premain-Class"))
          .isEqualTo("ai.narrativetrace.agent.NarrativeTraceAgent");
      assertThat(attributes.getValue("Can-Retransform-Classes")).isEqualTo("true");
    }
  }
}
