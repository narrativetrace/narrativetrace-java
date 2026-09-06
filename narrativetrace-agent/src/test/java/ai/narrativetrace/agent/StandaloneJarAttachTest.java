/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behavioral acceptance of the {@code -standalone} jar: attaching it to a JVM whose classpath
 * contains no NarrativeTrace classes at all must produce narration.
 *
 * <p>This is the legacy-attach scenario end to end: the fixture app is compiled into a bare
 * directory, the standalone jar rides in solely through {@code -javaagent}, and the user-supplied
 * SLF4J provider (slf4j-simple, injected by the build via {@code
 * narrativetrace.test.slf4jProviderJar}) reaches the JVM both supported ways — on the application
 * classpath (plain-java hosts) and via the {@code loggingJars} agent argument (app-server hosts
 * with no reachable classpath). If core were missing from the jar, the agent would die with {@code
 * NoClassDefFoundError} instead of narrating.
 */
class StandaloneJarAttachTest {

  private static final String FIXTURE_SOURCE =
      """
      package com.ntfixture;

      public class FixtureMain {
        public static void main(String[] args) {
          new FixtureMain().handleOrder("A-1");
        }

        String handleOrder(String orderId) {
          return "ok:" + orderId;
        }
      }
      """;

  @TempDir Path tempDir;

  @Test
  void attachingOnlyTheStandaloneJarProducesNarration() throws Exception {
    var fixtureDir = compileFixture();
    var process = launchFixtureJvm(fixtureDir, ClasspathShape.PROVIDER_ON_CLASSPATH);
    assertNarrationProduced(process);
  }

  @Test
  void loggingJarsArgumentInjectsProviderWithoutTouchingTheClasspath() throws Exception {
    var fixtureDir = compileFixture();
    var process = launchFixtureJvm(fixtureDir, ClasspathShape.PROVIDER_VIA_LOGGING_JARS);
    assertNarrationProduced(process);
  }

  /**
   * Silent by default: a minimal host that switches narration off with {@code loggerName=} and has
   * no SLF4J provider must print nothing at all.
   *
   * <p>The 2026-09-01 clean bug hunt saw three {@code SLF4J(W)} lines here. The cause was not the
   * missing provider — the standalone jar deliberately bundles none — but {@link AgentRuntime}'s
   * eagerly constructed default context, which built a pipeline narrating under the default logger
   * name before {@code initialize} could replace it with the one this attach asked for.
   */
  @Test
  void narrationSwitchedOffLeavesAHostWithNoProviderSilent() throws Exception {
    var fixtureDir = compileFixture();

    var process = launchSilentFixtureJvm(fixtureDir);
    var output = new String(process.getInputStream().readAllBytes());
    var finished = process.waitFor(60, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
    }

    assertThat(finished).as("fixture JVM must terminate; output so far:%n%s", output).isTrue();
    assertThat(process.exitValue()).as("fixture JVM exit code; output:%n%s", output).isZero();
    assertThat(output).as("a silenced agent must not initialise SLF4J at all").isEmpty();
  }

  private Process launchSilentFixtureJvm(Path fixtureClassesDir) throws IOException {
    var javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    var standaloneJar = requiredProperty("narrativetrace.test.standaloneJar");
    return new ProcessBuilder(
            javaBinary,
            "-javaagent:" + standaloneJar + "=packages=com.ntfixture,loggerName=",
            "-cp",
            fixtureClassesDir.toString(),
            "com.ntfixture.FixtureMain")
        .redirectErrorStream(true)
        .start();
  }

  private void assertNarrationProduced(Process process) throws Exception {
    var output = new String(process.getInputStream().readAllBytes());
    var finished = process.waitFor(60, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
    }

    assertThat(finished).as("fixture JVM must terminate; output so far:%n%s", output).isTrue();
    assertThat(process.exitValue()).as("fixture JVM exit code; output:%n%s", output).isZero();
    assertThat(output)
        .contains("FixtureMain.handleOrder()")
        .doesNotContain("com.ntfixture.FixtureMain.handleOrder()")
        .contains("returned: \"ok:A-1\"");
  }

  private Path compileFixture() throws IOException {
    var sourceFile = tempDir.resolve("FixtureMain.java");
    Files.writeString(sourceFile, FIXTURE_SOURCE);
    var classesDir = tempDir.resolve("classes");
    Files.createDirectories(classesDir);
    int result =
        ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, "-d", classesDir.toString(), sourceFile.toString());
    assertThat(result).as("fixture compilation exit code").isZero();
    return classesDir;
  }

  /** How the user-supplied SLF4J provider reaches the fixture JVM. */
  private enum ClasspathShape {
    /** Plain-java host: the provider sits on the application classpath. */
    PROVIDER_ON_CLASSPATH,
    /** App-server host: no reachable classpath, provider injected via the loggingJars arg. */
    PROVIDER_VIA_LOGGING_JARS
  }

  private Process launchFixtureJvm(Path fixtureClassesDir, ClasspathShape shape)
      throws IOException {
    var javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    var standaloneJar = requiredProperty("narrativetrace.test.standaloneJar");
    var providerJar = requiredProperty("narrativetrace.test.slf4jProviderJar");
    var agentArgs = "packages=com.ntfixture";
    var classpath = fixtureClassesDir.toString();
    if (shape == ClasspathShape.PROVIDER_ON_CLASSPATH) {
      classpath = classpath + java.io.File.pathSeparator + providerJar;
    } else {
      agentArgs = agentArgs + ",loggingJars=" + providerJar;
    }
    return new ProcessBuilder(
            javaBinary,
            "-javaagent:" + standaloneJar + "=" + agentArgs,
            "-Dorg.slf4j.simpleLogger.defaultLogLevel=trace",
            "-cp",
            classpath,
            "com.ntfixture.FixtureMain")
        .redirectErrorStream(true)
        .start();
  }

  private String requiredProperty(String name) {
    var value = System.getProperty(name);
    assertThat(value).as("%s system property", name).isNotNull();
    return value;
  }
}
