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
 * The opt-in deep traces, end to end with the packaged agent: the real {@code -standalone} jar
 * attached to a forked JVM, started with {@code depth=internal}, running a real JUnit 5 test
 * through the real {@code narrativetrace-junit5} extension — no fake runtime, no hand-recorded
 * spans.
 *
 * <p>INTENT: {@link DeepTraceTest} weaves {@code DeepSample} in-process and the integrations' own
 * tests stand a fake runtime in for the agent; neither can show that the three halves meet in a
 * real JVM — private methods woven at class load by the jar, a deep scope opened by the extension
 * through reflection, the mark and the header reaching the trace file. The class under test has a
 * public method whose private helper computes the wrong value, the case a default trace cannot
 * show; one test asks for depth with {@code @DeepTrace}, the other does not.
 */
class DeepAgentEndToEndTest {

  private static final String PRICING_SOURCE =
      """
      package com.ntfixture.shop;

      public class Pricing {
        public int total(int price, int quantity) {
          return withTax(price * quantity);
        }

        private int withTax(int net) {
          return net + (net / 10) + 1; // wrong on purpose: tax is added twice over
        }
      }
      """;

  private static final String TEST_SOURCE =
      """
      package com.ntfixture.tests;

      import ai.narrativetrace.api.annotation.DeepTrace;
      import ai.narrativetrace.junit5.NarrativeTraceExtension;
      import com.ntfixture.shop.Pricing;
      import org.junit.jupiter.api.Test;
      import org.junit.jupiter.api.extension.ExtendWith;

      @ExtendWith(NarrativeTraceExtension.class)
      public class PricingFixtureTest {

        @Test
        @DeepTrace
        void deepPricing() {
          new Pricing().total(100, 2);
        }

        @Test
        void plainPricing() {
          new Pricing().total(100, 2);
        }
      }
      """;

  private static final String LAUNCHER_SOURCE =
      """
      package com.ntfixture.tests;

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
                      .selectors(DiscoverySelectors.selectClass(PricingFixtureTest.class))
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

  private static Path fixtureClasses;

  @BeforeAll
  static void compileTheFixture() throws IOException {
    var sources = tempDir.resolve("src");
    Files.createDirectories(sources);
    var files =
        new String[] {
          write(sources, "Pricing.java", PRICING_SOURCE),
          write(sources, "PricingFixtureTest.java", TEST_SOURCE),
          write(sources, "LaunchMain.java", LAUNCHER_SOURCE)
        };
    fixtureClasses = tempDir.resolve("classes");
    Files.createDirectories(fixtureClasses);
    var args = new String[files.length + 5];
    args[0] = "-parameters"; // parameter names are what the trace shows for each call
    args[1] = "-d";
    args[2] = fixtureClasses.toString();
    args[3] = "-cp";
    args[4] = requiredProperty("narrativetrace.test.forkedJunit5Classpath");
    System.arraycopy(files, 0, args, 5, files.length);
    assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, args))
        .as("fixture compilation exit code")
        .isZero();
  }

  @Test
  void anAnnotatedTestShowsThePrivateCallMarkedAndNestedUnderThePublicOne() throws Exception {
    var run = runFixtureJvm("packages=com.ntfixture.shop,depth=internal");

    var deep = trace(run, "deep_pricing");
    assertThat(deep).contains("Depth: internal");
    assertThat(deep).contains("**Pricing.total**").contains("· **Pricing.withTax**");
    assertThat(deep.indexOf("· **Pricing.withTax**"))
        .as("the private call is nested under the public one")
        .isGreaterThan(deep.indexOf("**Pricing.total**"));
    assertThat(structural(run, "deep_pricing"))
        .contains("depth: internal\n")
        .contains("Pricing.withTax");
  }

  @Test
  void theSameRunWithoutTheAnnotationShowsNoPrivateCall() throws Exception {
    var run = runFixtureJvm("packages=com.ntfixture.shop,depth=internal");

    var plain = trace(run, "plain_pricing");
    assertThat(plain).contains("**Pricing.total**").doesNotContain("withTax");
    assertThat(plain).doesNotContain("Depth: internal").doesNotContain("·");
    assertThat(structural(run, "plain_pricing")).doesNotContain("depth:");
  }

  @Test
  void withoutTheAgentOptionTheAnnotationAsksForNothingAndTheTestSaysSo() throws Exception {
    var run = runFixtureJvm("packages=com.ntfixture.shop");

    var deep = trace(run, "deep_pricing");
    assertThat(deep).contains("**Pricing.total**").doesNotContain("withTax");
    assertThat(deep).doesNotContain("Depth: internal");
    assertThat(run.console()).contains("depth=internal was requested").contains("PUBLIC");
  }

  /** One forked run: where it wrote, and what it printed. */
  private record Run(Path outputDir, String console) {}

  private Run runFixtureJvm(String agentArgs) throws Exception {
    var outputDir = Files.createTempDirectory(tempDir, "out");
    var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    var classpath =
        fixtureClasses
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
                "com.ntfixture.tests.LaunchMain")
            .directory(tempDir.toFile())
            .redirectErrorStream(true)
            .start();
    var console = new String(process.getInputStream().readAllBytes());
    var finished = process.waitFor(120, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
    }
    assertThat(finished).as("forked JVM must terminate; output so far:%n%s", console).isTrue();
    assertThat(process.exitValue()).as("forked JVM exit code; output:%n%s", console).isZero();
    assertThat(console).contains("TESTS_SUCCEEDED=2 FAILED=0");
    return new Run(outputDir, console);
  }

  private static String trace(Run run, String slug) throws IOException {
    return Files.readString(only(run.outputDir().resolve("traces"), slug + ".md"));
  }

  private static String structural(Run run, String slug) throws IOException {
    return Files.readString(only(run.outputDir().resolve("structural"), slug + ".nt"));
  }

  private static Path only(Path dir, String fileName) throws IOException {
    try (Stream<Path> files = Files.walk(dir)) {
      var matches = files.filter(f -> f.getFileName().toString().equals(fileName)).toList();
      assertThat(matches).as("%s under %s", fileName, dir).hasSize(1);
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
