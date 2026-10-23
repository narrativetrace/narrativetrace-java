/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;
import org.testng.TestNG;
import org.testng.xml.XmlSuite;

/**
 * Runs TestNG fixtures in a nested {@code new TestNG()} — the harness every test in this module
 * drives, the way {@code narrativetrace-junit4}'s suite drives JUnit 4 fixtures from JUnit 5.
 *
 * <p>The listener reaches the nested run through TestNG's service loader, exactly as it reaches an
 * adopter's run: the module's own {@code META-INF/services/org.testng.ITestNGListener} is on this
 * test classpath. TestNG's default reporters are off and its report directory is a temp directory,
 * so a nested run writes nothing into the working tree.
 */
final class TestNgRuns {

  private TestNgRuns() {}

  /** What a nested run printed, whether TestNG judged it green, and its failed results. */
  record Run(String out, String err, boolean failed, List<ITestResult> failures) {

    /** The failure messages, in TestNG's order. */
    List<String> failureMessages() {
      return failures.stream().map(r -> String.valueOf(r.getThrowable().getMessage())).toList();
    }
  }

  /** Runs the classes with {@code narrativetrace.outputDir} pointed at {@code outputDir}. */
  static Run run(Path outputDir, Class<?>... classes) throws IOException {
    return run(outputDir, Map.of(), testng -> {}, classes);
  }

  /**
   * Runs the classes with the given extra system properties set for the run and cleared after it,
   * and a hook to configure the {@link TestNG} instance before it runs.
   */
  static Run run(
      Path outputDir,
      Map<String, String> properties,
      Consumer<TestNG> configure,
      Class<?>... classes)
      throws IOException {
    var props = new LinkedHashMap<String, String>();
    if (outputDir != null) {
      props.put("narrativetrace.outputDir", outputDir.toString());
    }
    props.putAll(properties);
    props.forEach(System::setProperty);
    try {
      return captured(configure, classes);
    } finally {
      props.keySet().forEach(System::clearProperty);
    }
  }

  /** One nested TestNG run with the console captured and TestNG's own reports out of the tree. */
  private static Run captured(Consumer<TestNG> configure, Class<?>... classes) throws IOException {
    var out = new ByteArrayOutputStream();
    var err = new ByteArrayOutputStream();
    var originalOut = System.out;
    var originalErr = System.err;
    var testng = new TestNG(false);
    testng.setUseDefaultListeners(false);
    testng.setOutputDirectory(Files.createTempDirectory("testng-reports").toString());
    testng.setVerbose(0);
    testng.setTestClasses(classes);
    var results = new TestListenerAdapter();
    testng.addListener(results);
    configure.accept(testng);
    System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
    try {
      testng.run();
    } finally {
      System.setOut(originalOut);
      System.setErr(originalErr);
    }
    return new Run(
        out.toString(StandardCharsets.UTF_8),
        err.toString(StandardCharsets.UTF_8),
        testng.hasFailure(),
        List.copyOf(results.getFailedTests()));
  }

  /** Runs the classes in parallel mode {@code methods} on {@code threads} worker threads. */
  static Run runParallelMethods(Path outputDir, int threads, Class<?>... classes)
      throws IOException {
    return run(
        outputDir,
        Map.of(),
        testng -> {
          testng.setParallel(XmlSuite.ParallelMode.METHODS);
          testng.setThreadCount(threads);
        },
        classes);
  }

  /**
   * A configuration hook that hides every service-loader listener from TestNG: its service-loader
   * class loader then sees no {@code META-INF/services} file at all, so only a listener a fixture
   * names in {@code @Listeners} is registered.
   */
  static Consumer<TestNG> withoutServiceLoaderListeners() {
    return testng ->
        testng.setServiceLoaderClassLoader(
            new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader()));
  }

  /** The text of a trace file under the run's {@code traces/} tree. */
  static String trace(Path outputDir, String fixture, String slug) throws IOException {
    return Files.readString(outputDir.resolve("traces/" + fixture + "/" + slug + ".md"));
  }

  /** Every file under {@code dir}, relative and sorted — a readable failure message. */
  static List<String> listing(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return List.of();
    }
    try (var files = Files.walk(dir)) {
      return files
          .filter(Files::isRegularFile)
          .map(f -> dir.relativize(f).toString().replace('\\', '/'))
          .sorted()
          .toList();
    }
  }
}
