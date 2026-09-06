/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.config.TracingLevel;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentConfigTest {

  @TempDir Path tempDir;

  @Test
  void parsesLoggingJarsAsExplicitFiles() throws Exception {
    var jarA = Files.createFile(tempDir.resolve("a.jar"));
    var jarB = Files.createFile(tempDir.resolve("b.jar"));

    var config = AgentConfig.parse("packages=com.x,loggingJars=" + jarA + ";" + jarB);

    assertThat(config.loggingJars()).containsExactly(jarA, jarB);
  }

  @Test
  void expandsLoggingJarsDirectoryToSortedJarsOnly() throws Exception {
    var dir = Files.createDirectory(tempDir.resolve("providers"));
    var jarB = Files.createFile(dir.resolve("b-provider.jar"));
    var jarA = Files.createFile(dir.resolve("a-provider.jar"));
    Files.createFile(dir.resolve("readme.txt"));

    var config = AgentConfig.parse("loggingJars=" + dir);

    assertThat(config.loggingJars()).containsExactly(jarA, jarB);
  }

  @Test
  void rejectsMissingLoggingJarsPathLoudly() {
    var missing = tempDir.resolve("no-such.jar");

    assertThatThrownBy(() -> AgentConfig.parse("loggingJars=" + missing))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("loggingJars path does not exist")
        .hasMessageContaining("no-such.jar");
  }

  @Test
  void absentLoggingJarsMeansEmptyList() {
    var config = AgentConfig.parse("packages=com.x");

    assertThat(config.loggingJars()).isEmpty();
  }

  @Test
  void resolvesLoggingJarsFromSystemPropertyWhenArgsAreBlank() throws Exception {
    var jar = Files.createFile(tempDir.resolve("prop-provider.jar"));
    System.setProperty("narrativetrace.loggingJars", jar.toString());
    try {
      var config = AgentConfig.parse(null);

      assertThat(config.loggingJars()).containsExactly(jar);
    } finally {
      System.clearProperty("narrativetrace.loggingJars");
    }
  }

  @Test
  void convenienceConstructorDefaultsToNoLoggingJars() {
    var config = new AgentConfig(List.of("com/x/"), "narrativetrace", TracingLevel.DETAIL);

    assertThat(config.loggingJars()).isEmpty();
  }

  @Test
  void identifiesTargetClassesByPackageFilter() {
    var config = AgentConfig.parse("packages=ai.narrativetrace.test;com.example");

    assertThat(config.shouldTransform("ai/narrativetrace/test/MyClass")).isTrue();
    assertThat(config.shouldTransform("com/example/Service")).isTrue();
    assertThat(config.shouldTransform("org/other/Class")).isFalse();
  }

  @Test
  void emptyPackagesTransformsNothing() {
    var config = AgentConfig.parse("");

    assertThat(config.shouldTransform("ai/narrativetrace/test/MyClass")).isFalse();
  }

  @Test
  void duplicateKeyThrows() {
    assertThatThrownBy(
            () -> AgentConfig.parse("packages=ai.narrativetrace.test,packages=com.example"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Duplicate agent config key: packages");
  }

  @Test
  void parsesNullArgs() {
    var config = AgentConfig.parse(null);

    assertThat(config.packages()).isEmpty();
  }

  @Test
  void rejectsPartWithoutEqualsSign() {
    assertThatThrownBy(() -> AgentConfig.parse("packages=ai.narrativetrace.test,malformed"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid agent config entry: malformed");
  }

  @Test
  void emptyPackagesValueDoesNotMatchEverything() {
    var config = AgentConfig.parse("packages=");

    assertThat(config.shouldTransform("com/example/Foo")).isFalse();
  }

  @Test
  void ignoresUnknownKey() {
    var config = AgentConfig.parse("debug=true,packages=ai.narrativetrace.test");

    assertThat(config.shouldTransform("ai/narrativetrace/test/MyClass")).isTrue();
  }

  @Test
  void wildcardPackageMatchesSubpackages() {
    var config = AgentConfig.parse("packages=com.example.*");

    assertThat(config.shouldTransform("com/example/Service")).isTrue();
    assertThat(config.shouldTransform("com/example/app/Service")).isTrue();
    assertThat(config.shouldTransform("com/examplefoo/Service")).isFalse();
    assertThat(config.shouldTransform("org/other/Class")).isFalse();
  }

  @Test
  void doubleWildcardPackageMatchesSubpackages() {
    var config = AgentConfig.parse("packages=com.example.**");

    assertThat(config.shouldTransform("com/example/Service")).isTrue();
    assertThat(config.shouldTransform("com/example/app/deep/Service")).isTrue();
    assertThat(config.shouldTransform("com/examplefoo/Service")).isFalse();
  }

  @Test
  void barePackageEnforcesBoundary() {
    var config = AgentConfig.parse("packages=com.example");

    assertThat(config.shouldTransform("com/example/Service")).isTrue();
    assertThat(config.shouldTransform("com/examplefoo/Service")).isFalse();
  }

  @Test
  void parsesLoggerNameFromAgentArgs() {
    var config = AgentConfig.parse("packages=com.example,loggerName=myapp.traces");

    assertThat(config.loggerName()).isEqualTo("myapp.traces");
  }

  @Test
  void loggerNameDefaultsToNarrativetrace() {
    var config = AgentConfig.parse("packages=com.example");

    assertThat(config.loggerName()).isEqualTo("narrativetrace");
  }

  @Test
  void parsesLevelFromAgentArgs() {
    var config = AgentConfig.parse("packages=com.example,level=NARRATIVE");

    assertThat(config.level()).isEqualTo(ai.narrativetrace.api.config.TracingLevel.NARRATIVE);
  }

  @Test
  void levelDefaultsToDetailWhenAbsent() {
    var config = AgentConfig.parse("packages=com.example");

    assertThat(config.level()).isEqualTo(ai.narrativetrace.api.config.TracingLevel.DETAIL);
  }

  @Test
  void levelFromPropertiesFile(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
    Files.writeString(
        tempDir.resolve("narrativetrace.properties"),
        "narrativetrace.packages=com.example\nnarrativetrace.level=OFF\n");
    var classLoader = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null);

    var config = AgentConfig.parse(null, classLoader);

    assertThat(config.level()).isEqualTo(ai.narrativetrace.api.config.TracingLevel.OFF);
  }

  @Test
  void loggerNameDefaultsToNarrativetraceWhenNullArgs() {
    var config = AgentConfig.parse(null);

    assertThat(config.loggerName()).isEqualTo("narrativetrace");
  }

  @Test
  void fallsBackToConfigResolverWhenNoCliArgs(@org.junit.jupiter.api.io.TempDir Path tempDir)
      throws Exception {
    Files.writeString(
        tempDir.resolve("narrativetrace.properties"),
        "narrativetrace.packages=com.example.app;com.example.shared\n");
    var classLoader = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null);
    var config = AgentConfig.parse(null, classLoader);

    assertThat(config.shouldTransform("com/example/app/Service")).isTrue();
    assertThat(config.shouldTransform("com/example/shared/Util")).isTrue();
    assertThat(config.shouldTransform("org/other/Foo")).isFalse();
  }

  @Test
  void loggerNameFromPropertiesFile(@org.junit.jupiter.api.io.TempDir Path tempDir)
      throws Exception {
    Files.writeString(
        tempDir.resolve("narrativetrace.properties"),
        "narrativetrace.packages=com.example\nnarrativetrace.loggerName=myapp.traces\n");
    var classLoader = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null);
    var config = AgentConfig.parse(null, classLoader);

    assertThat(config.loggerName()).isEqualTo("myapp.traces");
  }
}
