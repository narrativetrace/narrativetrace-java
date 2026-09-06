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
 * Composition contract of the main (slim) agent jar — the artifact for <em>builds</em>, where the
 * POM resolves core like any dependency.
 *
 * <p>ASM must be bundled (relocated to an internal package so it can never clash with an
 * application's own ASM), while {@code narrativetrace-core} must NOT be bundled — the agent shares
 * core types with the application, so core stays a declared POM dependency. Stand-alone {@code
 * -javaagent} attach is served by the {@code -standalone} classifier instead (see {@link
 * StandaloneJarCompositionTest}); nothing resolves the POM at attach time, so this slim jar alone
 * would fail with {@code NoClassDefFoundError} there.
 *
 * <p>The jar under test is supplied by the build via the {@code narrativetrace.test.agentJar}
 * system property.
 */
class AgentJarSelfContainmentTest {

  @Test
  void publishedAgentJarBundlesRelocatedAsmOnly() throws Exception {
    var jarPath = System.getProperty("narrativetrace.test.agentJar");
    assertThat(jarPath).as("narrativetrace.test.agentJar system property").isNotNull();

    try (var jar = new JarFile(jarPath)) {
      List<String> entries = jar.stream().map(ZipEntry::getName).toList();

      assertThat(entries).contains("ai/narrativetrace/agent/NarrativeTraceAgent.class");
      assertThat(entries)
          .as("ASM must be bundled under the relocated internal package")
          .anyMatch(name -> name.startsWith("ai/narrativetrace/agent/internal/asm/"));
      assertThat(entries)
          .as("original ASM packages must not leak into the jar")
          .noneMatch(name -> name.startsWith("org/objectweb/asm/"));
      assertThat(entries)
          .as("core is a declared dependency, never bundled")
          .noneMatch(name -> name.startsWith("ai/narrativetrace/core/"));
      assertThat(entries)
          .as("the api jar arrives through core's POM the same way — never bundled either")
          .noneMatch(name -> name.startsWith("ai/narrativetrace/api/"));
    }
  }

  @Test
  void publishedAgentJarDeclaresPremainManifest() throws Exception {
    var jarPath = System.getProperty("narrativetrace.test.agentJar");
    assertThat(jarPath).as("narrativetrace.test.agentJar system property").isNotNull();

    try (var jar = new JarFile(jarPath)) {
      var attributes = jar.getManifest().getMainAttributes();
      assertThat(attributes.getValue("Premain-Class"))
          .isEqualTo("ai.narrativetrace.agent.NarrativeTraceAgent");
      assertThat(attributes.getValue("Can-Retransform-Classes")).isEqualTo("true");
    }
  }
}
