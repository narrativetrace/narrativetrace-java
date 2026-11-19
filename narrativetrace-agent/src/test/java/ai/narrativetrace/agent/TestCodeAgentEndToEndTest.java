/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test code is not instrumented by default, end to end with the packaged agent: the real {@code
 * -standalone} jar attached to a forked JVM, a production class loaded from a {@code
 * target/classes} directory and a JUnit 5 test loaded from a {@code target/test-classes} directory,
 * both inside the one {@code packages=} the agent is given.
 *
 * <p>INTENT: {@link TestCodeSkipTransformTest} drives the decision with constructed protection
 * domains; only a real JVM shows that the protection domain the JVM hands the transformer for a
 * directory-loaded class really carries the output directory, and that the trace the test
 * integration writes then has the production call as its root.
 */
class TestCodeAgentEndToEndTest {

  private static final String PRICING_SOURCE =
      """
      package com.ntfixture.shop;

      public class Pricing {
        public int total(int price, int quantity) {
          return price * quantity;
        }
      }
      """;

  private static final String TEST_SOURCE =
      """
      package com.ntfixture.shop;

      import ai.narrativetrace.junit5.NarrativeTraceExtension;
      import org.junit.jupiter.api.Test;
      import org.junit.jupiter.api.extension.ExtendWith;

      @ExtendWith(NarrativeTraceExtension.class)
      public class PricingFixtureTest {

        @Test
        void pricing() {
          assertTotal(new Pricing().total(100, 2));
        }

        void assertTotal(int total) {
          if (total != 200) {
            throw new IllegalStateException("total " + total);
          }
        }
      }
      """;

  /** Calls the production method, then fails on an assertion whose message is recognisable. */
  private static final String FAILING_TEST_SOURCE =
      """
      package com.ntfixture.shop;

      import static org.junit.jupiter.api.Assertions.assertEquals;

      import ai.narrativetrace.junit5.NarrativeTraceExtension;
      import org.junit.jupiter.api.Test;
      import org.junit.jupiter.api.extension.ExtendWith;

      @ExtendWith(NarrativeTraceExtension.class)
      public class FailingPricingFixtureTest {

        @Test
        void pricing() {
          int actual = new Pricing().total(100, 2);
          assertEquals(999, actual, "total for the reference basket");
        }
      }
      """;

  private static final String LAUNCHER_SOURCE =
      """
      package com.ntlaunch;

      import org.junit.platform.engine.discovery.DiscoverySelectors;
      import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
      import org.junit.platform.launcher.core.LauncherFactory;
      import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

      public class LaunchMain {
        public static void main(String[] args) {
          var listener = new SummaryGeneratingListener();
          LauncherFactory.create()
              .execute(
                  LauncherDiscoveryRequestBuilder.request()
                      .selectors(DiscoverySelectors.selectClass(args[0]))
                      .build(),
                  listener);
          var summary = listener.getSummary();
          System.out.println(
              "TESTS_SUCCEEDED=" + summary.getTestsSucceededCount()
                  + " FAILED=" + summary.getTestsFailedCount());
          System.exit(summary.getTestsFailedCount() == 0 ? 0 : 1);
        }
      }
      """;

  @TempDir static Path tempDir;

  private static Path productionClasses;
  private static Path testClasses;

  @BeforeAll
  static void compileProductionAndTestIntoSeparateOutputDirectories() throws IOException {
    var sources = tempDir.resolve("src");
    Files.createDirectories(sources);
    productionClasses = tempDir.resolve("target").resolve("classes");
    testClasses = tempDir.resolve("target").resolve("test-classes");
    Files.createDirectories(productionClasses);
    Files.createDirectories(testClasses);
    var junitClasspath = requiredProperty("narrativetrace.test.forkedJunit5Classpath");
    compile(productionClasses, junitClasspath, write(sources, "Pricing.java", PRICING_SOURCE));
    compile(
        testClasses,
        productionClasses + File.pathSeparator + junitClasspath,
        write(sources, "PricingFixtureTest.java", TEST_SOURCE),
        write(sources, "FailingPricingFixtureTest.java", FAILING_TEST_SOURCE),
        write(sources, "LaunchMain.java", LAUNCHER_SOURCE));
  }

  private static void compile(Path outputDir, String classpath, String... files) {
    var args = new String[files.length + 5];
    args[0] = "-parameters"; // parameter names are what the trace shows for each call
    args[1] = "-d";
    args[2] = outputDir.toString();
    args[3] = "-cp";
    args[4] = classpath;
    System.arraycopy(files, 0, args, 5, files.length);
    assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, args))
        .as("fixture compilation exit code for %s", outputDir)
        .isZero();
  }

  @Test
  void byDefaultTheProductionCallIsTheRootAndTheTestAndItsHelperAreNotRecorded() throws Exception {
    var run = runFixtureJvm("packages=com.ntfixture");

    var structural = structural(run);
    assertThat(structural).contains("#1 - Pricing.total");
    assertThat(structural).doesNotContain("PricingFixtureTest");
    var trace = trace(run);
    assertThat(trace).contains("entry_point: Pricing.total");
    assertThat(trace.substring(trace.indexOf("### Call Flow")))
        .contains("**Pricing.total**")
        .doesNotContain("PricingFixtureTest")
        .doesNotContain("assertTotal");
    assertThat(run.console()).contains("tests=trace");
  }

  @Test
  void withTestsTraceTheTestMethodIsTheRootAndTheProductionCallIsNestedUnderIt() throws Exception {
    var run = runFixtureJvm("packages=com.ntfixture,tests=trace");

    var structural = structural(run);
    assertThat(structural).contains("#1 - PricingFixtureTest.pricing");
    assertThat(structural).contains("#1.1 - Pricing.total");
    assertThat(trace(run)).contains("entry_point: PricingFixtureTest.pricing");
    assertThat(run.console()).doesNotContain("not instrumenting");
  }

  @Test
  void aTestThatFailsOnAnAssertionStatesItsMessageInTheTraceFileHeaderAndBody() throws Exception {
    var run = runFailingFixtureJvm("packages=com.ntfixture");

    var structural = run.outputDir().resolve("structural");
    assertThat(structural).as("a failing run writes no structural artifact").doesNotExist();
    var trace = trace(run);
    assertThat(trace.substring(trace.indexOf("### Call Flow")))
        .contains("**Pricing.total**")
        .doesNotContain("FailingPricingFixtureTest")
        .doesNotContain("❌");
    assertThat(trace)
        .contains("Failing call: none, no recorded call threw; the test itself failed: ")
        .contains("total for the reference basket")
        .contains("**Test failure:** total for the reference basket");
    assertThat(index(run)).contains("total for the reference basket");
  }

  /** One forked run: where it wrote, and what it printed. */
  private record Run(Path outputDir, String console) {}

  private Run runFixtureJvm(String agentArgs) throws Exception {
    var run = launch(agentArgs, "com.ntfixture.shop.PricingFixtureTest", 0);
    assertThat(run.console()).contains("TESTS_SUCCEEDED=1 FAILED=0");
    return run;
  }

  /** The fixture whose test fails on an assertion: the forked JVM exits 1 and says FAILED=1. */
  private Run runFailingFixtureJvm(String agentArgs) throws Exception {
    var run = launch(agentArgs, "com.ntfixture.shop.FailingPricingFixtureTest", 1);
    assertThat(run.console()).contains("TESTS_SUCCEEDED=0 FAILED=1");
    return run;
  }

  private Run launch(String agentArgs, String fixtureClass, int expectedExit) throws Exception {
    var outputDir = Files.createTempDirectory(tempDir, "out");
    var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    var classpath =
        testClasses
            + File.pathSeparator
            + productionClasses
            + File.pathSeparator
            + requiredProperty("narrativetrace.test.forkedJunit5Classpath");
    var process =
        new ProcessBuilder(
                java,
                "-javaagent:"
                    + requiredProperty("narrativetrace.test.standaloneJar")
                    + "="
                    + agentArgs,
                "-Dnarrativetrace.outputDir=" + outputDir,
                "-cp",
                classpath,
                "com.ntlaunch.LaunchMain",
                fixtureClass)
            .directory(tempDir.toFile())
            .redirectErrorStream(true)
            .start();
    var console = new String(process.getInputStream().readAllBytes());
    var finished = process.waitFor(120, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
    }
    assertThat(finished).as("forked JVM must terminate; output so far:%n%s", console).isTrue();
    assertThat(process.exitValue())
        .as("forked JVM exit code; output:%n%s", console)
        .isEqualTo(expectedExit);
    return new Run(outputDir, console);
  }

  private static String trace(Run run) throws IOException {
    return Files.readString(only(run.outputDir().resolve("traces"), ".md"));
  }

  private static String index(Run run) throws IOException {
    return Files.readString(run.outputDir().resolve("INDEX.md"));
  }

  private static String structural(Run run) throws IOException {
    return Files.readString(only(run.outputDir().resolve("structural"), ".nt"));
  }

  private static Path only(Path dir, String suffix) throws IOException {
    try (Stream<Path> files = Files.walk(dir)) {
      var matches = files.filter(f -> f.getFileName().toString().endsWith(suffix)).toList();
      assertThat(matches).as("*%s under %s", suffix, dir).hasSize(1);
      return matches.get(0);
    }
  }

  private static String write(Path dir, String name, String source) throws IOException {
    return Files.writeString(dir.resolve(name), source).toString();
  }

  private static String requiredProperty(String name) {
    var value = System.getProperty(name);
    assertThat(value).as("%s system property", name).isNotNull();
    return value;
  }
}
