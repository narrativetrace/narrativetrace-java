/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/test/java/com/example/checkout/RecipeRuns.java
package com.example.checkout;

import java.nio.file.Path;
import java.util.function.Supplier;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * INTENT: Runs a {@code *Fixture} through the JUnit Platform launcher, in process, with the trace
 * output and approval directories this run is supposed to use — the one piece of machinery every
 * recipe whose evidence is a FAILING or an APPROVED run needs.
 *
 * <p>A fixture's artifacts are written straight into the directory a recipe page embeds from, so
 * nothing is copied afterwards and the page shows the file the extension itself wrote.
 *
 * <p><b>@llmNote</b> Every property is restored, not cleared: the Gradle test task sets {@code
 * narrativetrace.outputDir} for the whole suite, and a cleared property would silently move the
 * artifacts of whichever test ran next. An approved directory is never pointed at the source tree —
 * an approval mismatch writes a {@code .received.nt} beside the baseline, and a verification task
 * may not write where the repository lives.
 */
final class RecipeRuns {

  private RecipeRuns() {}

  /** Runs [selector] with trace output in [outputDir]; approval off. */
  static TestExecutionSummary run(DiscoverySelector selector, Path outputDir) {
    return withProperties(
        () -> execute(selector),
        "narrativetrace.output",
        "true",
        "narrativetrace.outputDir",
        outputDir.toString());
  }

  /** Runs [selector] with trace output in [outputDir] and approval against [approvedDir]. */
  static TestExecutionSummary runUnderApproval(
      DiscoverySelector selector, Path outputDir, Path approvedDir) {
    return withProperties(
        () ->
            withProperties(
                () -> execute(selector),
                "narrativetrace.approval",
                "true",
                "narrativetrace.approvedDir",
                approvedDir.toString()),
        "narrativetrace.output",
        "true",
        "narrativetrace.outputDir",
        outputDir.toString());
  }

  private static TestExecutionSummary execute(DiscoverySelector selector) {
    var request = LauncherDiscoveryRequestBuilder.request().selectors(selector).build();
    var listener = new SummaryGeneratingListener();
    LauncherFactory.create().execute(request, listener);
    return listener.getSummary();
  }

  private static TestExecutionSummary withProperties(
      Supplier<TestExecutionSummary> body,
      String firstKey,
      String firstValue,
      String secondKey,
      String secondValue) {
    var previousFirst = System.getProperty(firstKey);
    var previousSecond = System.getProperty(secondKey);
    System.setProperty(firstKey, firstValue);
    System.setProperty(secondKey, secondValue);
    try {
      return body.get();
    } finally {
      restore(firstKey, previousFirst);
      restore(secondKey, previousSecond);
    }
  }

  private static void restore(String key, String previous) {
    if (previous == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, previous);
    }
  }
}
