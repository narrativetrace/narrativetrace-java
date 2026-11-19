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
import java.util.stream.Collectors;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A test whose traced code recurses until {@link StackOverflowError}, caught by the code under
 * test, must not cost the tests after it their traces (B-342).
 *
 * <p>INTENT: The real {@code -standalone} jar attached to a forked JVM and the real JUnit 5
 * extension, under the default {@code tests=skip}: the recursion is a woven production {@code
 * toString}, the catch is a production formatter. When the stack runs out inside the ring's
 * publish, between the slot claim and the write, the claimed slot is never written; every later
 * drain stops in front of it, so the next tests' events are never seen and their trace files are
 * never written — until enough events pile up behind the slot to lap the ring.
 *
 * <p><b>@llmNote</b> Two knobs make the run reproduce rather than wait for luck. {@code -Xss256k}
 * keeps one recursion's events (about a thousand) below the 8,192 slots the JUnit integration gives
 * its ring, so the recursion's own events cannot lap the abandoned slot — the observed run had the
 * same proportion naturally (8,158 events shed by a ring of 8,192). The overflow test overflows
 * from several base depths, because where the stack runs out inside the publish path moves with the
 * depth the recursion starts at, and only some starting depths land between claim and write.
 */
class StackOverflowAgentEndToEndTest {

  private static final String PRICING_SOURCE =
      """
      package com.ntfixture.shop;

      public class Pricing {
        public int total(int price, int quantity) {
          return price * quantity;
        }
      }
      """;

  private static final String SELF_DESCRIBING_SOURCE =
      """
      package com.ntfixture.shop;

      public class SelfDescribing {
        @Override
        public String toString() {
          return describe();
        }

        private String describe() {
          return toString();
        }
      }
      """;

  private static final String FORMATTER_SOURCE =
      """
      package com.ntfixture.shop;

      public class ValueFormatter {
        public String format(Object value) {
          try {
            return String.valueOf(value);
          } catch (StackOverflowError exhausted) {
            return "<unavailable>";
          }
        }
      }
      """;

  private static final String TEST_SOURCE =
      """
      package com.ntfixture.shop;

      import ai.narrativetrace.junit5.NarrativeTraceExtension;
      import org.junit.jupiter.api.MethodOrderer;
      import org.junit.jupiter.api.Order;
      import org.junit.jupiter.api.Test;
      import org.junit.jupiter.api.TestMethodOrder;
      import org.junit.jupiter.api.extension.ExtendWith;

      @ExtendWith(NarrativeTraceExtension.class)
      @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
      public class OverflowFixtureTest {

        @Test
        @Order(1)
        void pricesBeforeTheOverflow() {
          check(new Pricing().total(100, 2) == 200);
        }

        @Test
        @Order(2)
        void formatsAValueWhoseToStringExhaustsTheStack() {
          for (int depth = 0; depth < 16; depth++) {
            check("<unavailable>".equals(formatAtDepth(depth, new SelfDescribing())));
          }
        }

        @Test
        @Order(3)
        void pricesFirstAfterTheOverflow() {
          check(new Pricing().total(100, 3) == 300);
        }

        @Test
        @Order(4)
        void pricesSecondAfterTheOverflow() {
          check(new Pricing().total(100, 4) == 400);
        }

        @Test
        @Order(5)
        void pricesThirdAfterTheOverflow() {
          check(new Pricing().total(100, 5) == 500);
        }

        /** Test code, not woven: only moves the depth the formatter is called from. */
        private static String formatAtDepth(int frames, Object value) {
          return frames == 0
              ? new ValueFormatter().format(value)
              : formatAtDepth(frames - 1, value);
        }

        private static void check(boolean condition) {
          if (!condition) {
            throw new IllegalStateException("unexpected result");
          }
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

  private static final String[] TESTS_AFTER_THE_OVERFLOW = {
    "prices_first_after_the_overflow",
    "prices_second_after_the_overflow",
    "prices_third_after_the_overflow"
  };

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
    compile(
        productionClasses,
        junitClasspath,
        write(sources, "Pricing.java", PRICING_SOURCE),
        write(sources, "SelfDescribing.java", SELF_DESCRIBING_SOURCE),
        write(sources, "ValueFormatter.java", FORMATTER_SOURCE));
    compile(
        testClasses,
        productionClasses + File.pathSeparator + junitClasspath,
        write(sources, "OverflowFixtureTest.java", TEST_SOURCE),
        write(sources, "LaunchMain.java", LAUNCHER_SOURCE));
  }

  @Test
  void everyTestAfterACaughtStackOverflowStillWritesItsTraceFile() throws Exception {
    var run = launch();

    for (var test : TESTS_AFTER_THE_OVERFLOW) {
      assertThat(traceFile(run, test))
          .as("trace file of %s; console (call-tree lines omitted):%n%s", test, run.console())
          .exists();
      assertThat(Files.readString(run.outputDir().resolve("INDEX.md")))
          .as("index row of %s", test)
          .contains("traces/OverflowFixtureTest/" + test + ".md");
    }
  }

  /** One forked run: where it wrote, and what it printed. */
  private record Run(Path outputDir, String console) {}

  private static Run launch() throws Exception {
    var outputDir = Files.createTempDirectory(tempDir, "out");
    var process = forkedJvm(outputDir).start();
    var console = new String(process.getInputStream().readAllBytes());
    var finished = process.waitFor(120, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
    }
    assertThat(finished).as("forked JVM must terminate").isTrue();
    var summary = withoutCallTrees(console);
    assertThat(summary).contains("TESTS_SUCCEEDED=5 FAILED=0");
    return new Run(outputDir, summary);
  }

  private static ProcessBuilder forkedJvm(Path outputDir) {
    var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    var classpath =
        testClasses
            + File.pathSeparator
            + productionClasses
            + File.pathSeparator
            + requiredProperty("narrativetrace.test.forkedJunit5Classpath");
    return new ProcessBuilder(
            java,
            "-Xss256k",
            "-javaagent:"
                + requiredProperty("narrativetrace.test.standaloneJar")
                + "=packages=com.ntfixture",
            "-Dnarrativetrace.outputDir=" + outputDir,
            "-cp",
            classpath,
            "com.ntlaunch.LaunchMain",
            "com.ntfixture.shop.OverflowFixtureTest")
        .directory(tempDir.toFile())
        .redirectErrorStream(true);
  }

  /** The console without the printed call trees, which for the recursion run to megabytes. */
  private static String withoutCallTrees(String console) {
    return console
        .lines()
        .filter(
            line -> line.length() < 400 && line.chars().noneMatch(c -> c >= 0x2500 && c < 0x2580))
        .filter(line -> !line.contains("ASSERTION FAILED"))
        .collect(Collectors.joining(System.lineSeparator()));
  }

  private static Path traceFile(Run run, String test) {
    return run.outputDir().resolve("traces").resolve("OverflowFixtureTest").resolve(test + ".md");
  }

  private static void compile(Path outputDir, String classpath, String... files) {
    var args = new String[files.length + 5];
    args[0] = "-parameters";
    args[1] = "-d";
    args[2] = outputDir.toString();
    args[3] = "-cp";
    args[4] = classpath;
    System.arraycopy(files, 0, args, 5, files.length);
    assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, args))
        .as("fixture compilation exit code for %s", outputDir)
        .isZero();
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
