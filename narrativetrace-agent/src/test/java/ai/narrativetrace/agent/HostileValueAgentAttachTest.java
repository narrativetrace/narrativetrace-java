/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.config.TracingLevel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Regression for an earlier bug hunt's most severe, release-blocking finding: with the real agent
 * attached, a hostile parameter or return value must not change what the instrumented method does.
 *
 * <p>INTENT: {@link AgentRuntimeTest} calls the hooks directly, which cannot see the one thing that
 * made this finding release-blocking — the injected call sequence. {@code enterMethod} is emitted
 * <em>before</em> the synthetic try range opens, so a throw from it skips the method body entirely;
 * the exit hooks sit inside the range, so a throw from them is rethrown at the caller in place of
 * the value. Only a real {@code -javaagent} run exercises that shape.
 *
 * <p><b>@edgeCase</b> The hostile collection lives in a package the agent is <em>not</em> asked to
 * transform, so the failure comes from rendering it rather than from tracing its own methods.
 *
 * <p><b>@llmNote</b> The level-parameterised test is the agent half of the supplement's release
 * gate matrix: it is not enough to know {@code DETAIL} is safe, because the supplement reproduced
 * the poisoning at {@code ERRORS}, {@code SUMMARY} and {@code NARRATIVE} too — return values are
 * rendered at every active level, unlike parameters. Each row is a separate JVM with the real
 * {@code -javaagent} jar; the fixture is compiled once and reused.
 */
class HostileValueAgentAttachTest {

  private static final String HOSTILE_SOURCE =
      """
      package com.hostile;

      import java.util.AbstractCollection;
      import java.util.Iterator;

      /** A Collection that throws from every method the renderer touches. */
      public class ExplodingCollection extends AbstractCollection<String> {
        @Override
        public Iterator<String> iterator() {
          throw new IllegalStateException("iterator crashed");
        }

        @Override
        public int size() {
          throw new IllegalStateException("size crashed");
        }
      }
      """;

  private static final String FIXTURE_SOURCE =
      """
package com.ntfixture;

import com.hostile.ExplodingCollection;
import java.util.Collection;

public class FixtureMain {
  private static int calls;

  public static void main(String[] args) {
    var app = new FixtureMain();
    app.warmup("A-1");
    try {
      String result = app.handleOrder(new ExplodingCollection());
      System.out.println("CALL_RETURNED result=" + result + " calls=" + calls);
    } catch (Throwable t) {
      System.out.println("CALL_FAILED " + t + " calls=" + calls);
    }
    try {
      Collection<String> produced = app.produceOrders();
      System.out.println("RETURN_RECEIVED hostile=" + (produced instanceof ExplodingCollection));
    } catch (Throwable t) {
      System.out.println("RETURN_FAILED " + t);
    }
  }

  String warmup(String orderId) {
    return "ok:" + orderId;
  }

  String handleOrder(Collection<String> items) {
    calls++;
    return "ok";
  }

  Collection<String> produceOrders() {
    return new ExplodingCollection();
  }
}
""";

  @TempDir static Path tempDir;

  /** Compiled once: the fixture is identical for every level, only the agent argument differs. */
  private static Path fixtureClasses;

  @ParameterizedTest
  @EnumSource(TracingLevel.class)
  void aHostileValueNeverChangesWhatTheMethodDoesAtAnyLevel(TracingLevel level) throws Exception {
    var output = runFixtureUnderAgent(level);

    assertThat(output)
        .as(
            "the supplement's level matrix, black-box under the real agent jar at %s:%n%s",
            level, output)
        .contains("CALL_RETURNED result=ok calls=1")
        .contains("RETURN_RECEIVED hostile=true")
        .doesNotContain("CALL_FAILED")
        .doesNotContain("RETURN_FAILED");
  }

  @Test
  void aParameterThatThrowsWhileRenderingStillLetsTheMethodBodyRun() throws Exception {
    var output = runFixtureUnderAgent();

    assertThat(output)
        .as("the traced method must still execute and return its own value")
        .contains("CALL_RETURNED result=ok calls=1")
        .doesNotContain("CALL_FAILED");
  }

  @Test
  void aReturnValueThatThrowsWhileRenderingIsStillReturnedToTheCaller() throws Exception {
    var output = runFixtureUnderAgent();

    assertThat(output)
        .as("the business return value must win over a rendering failure")
        .contains("RETURN_RECEIVED hostile=true")
        .doesNotContain("RETURN_FAILED");
  }

  @Test
  void theAgentIsActuallyTracingWhileTheHostileValuesAreIgnored() throws Exception {
    var output = runFixtureUnderAgent();

    assertThat(output)
        .as("a benign method on the same class still narrates, so tracing really was on")
        .contains("FixtureMain.warmup(orderId: \"A-1\")")
        .contains("returned: \"ok:A-1\"");
  }

  private String runFixtureUnderAgent() throws Exception {
    return runFixtureUnderAgent(TracingLevel.DETAIL);
  }

  private String runFixtureUnderAgent(TracingLevel level) throws Exception {
    var process = launchFixtureJvm(compiledFixture(), level);
    var output = new String(process.getInputStream().readAllBytes());
    var finished = process.waitFor(60, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
    }
    assertThat(finished).as("fixture JVM must terminate; output so far:%n%s", output).isTrue();
    assertThat(process.exitValue()).as("fixture JVM exit code; output:%n%s", output).isZero();
    return output;
  }

  private static synchronized Path compiledFixture() throws IOException {
    if (fixtureClasses == null) {
      fixtureClasses = compileFixture();
    }
    return fixtureClasses;
  }

  private static Path compileFixture() throws IOException {
    var hostileFile = tempDir.resolve("ExplodingCollection.java");
    Files.writeString(hostileFile, HOSTILE_SOURCE);
    var fixtureFile = tempDir.resolve("FixtureMain.java");
    Files.writeString(fixtureFile, FIXTURE_SOURCE);
    var classesDir = tempDir.resolve("classes");
    Files.createDirectories(classesDir);
    int result =
        ToolProvider.getSystemJavaCompiler()
            .run(
                null,
                null,
                null,
                "-parameters",
                "-d",
                classesDir.toString(),
                hostileFile.toString(),
                fixtureFile.toString());
    assertThat(result).as("fixture compilation exit code").isZero();
    return classesDir;
  }

  private static Process launchFixtureJvm(Path fixtureClassesDir, TracingLevel level)
      throws IOException {
    var javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    var standaloneJar = requiredProperty("narrativetrace.test.standaloneJar");
    var providerJar = requiredProperty("narrativetrace.test.slf4jProviderJar");
    return new ProcessBuilder(
            javaBinary,
            "-javaagent:" + standaloneJar + "=packages=com.ntfixture,level=" + level,
            "-Dorg.slf4j.simpleLogger.defaultLogLevel=trace",
            "-cp",
            fixtureClassesDir + java.io.File.pathSeparator + providerJar,
            "com.ntfixture.FixtureMain")
        .redirectErrorStream(true)
        .start();
  }

  private static String requiredProperty(String name) {
    var value = System.getProperty(name);
    assertThat(value).as("%s system property", name).isNotNull();
    return value;
  }
}
