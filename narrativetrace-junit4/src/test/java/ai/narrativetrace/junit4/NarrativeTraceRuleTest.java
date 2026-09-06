/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.runner.Description;

class NarrativeTraceRuleTest {

  @Test
  void contextIsNullBeforeStarting() {
    var rule = new NarrativeTraceRule();

    assertThat(rule.context()).isNull();
  }

  @Test
  void startingCreatesThreadLocalContext() {
    var rule = new NarrativeTraceRule();
    rule.starting(Description.createTestDescription(getClass(), "test"));

    assertThat(rule.context()).isNotNull();
    assertThat(rule.context()).isInstanceOf(ThreadLocalNarrativeContext.class);
  }

  @Test
  void eachStartingCreatesNewContext() {
    var rule = new NarrativeTraceRule();
    rule.starting(Description.createTestDescription(getClass(), "test1"));
    var first = rule.context();
    rule.starting(Description.createTestDescription(getClass(), "test2"));

    assertThat(rule.context()).isNotSameAs(first);
  }

  @Test
  void newContextHonorsOffLevelSoNothingIsCaptured() {
    assertThat(NarrativeTraceRule.newContext("OFF").isActive()).isFalse();
  }

  @Test
  void newContextDefaultsToDetailForNullOrUnknownLevel() {
    assertThat(NarrativeTraceRule.newContext(null).isActive()).isTrue();
    assertThat(NarrativeTraceRule.newContext("bogus").isActive()).isTrue();
  }

  @Test
  void failedPrintsFailureReportWhenTraceIsNonEmpty() {
    var rule = new NarrativeTraceRule();
    var out = new ByteArrayOutputStream();
    rule.setOut(new PrintStream(out));
    var desc = Description.createTestDescription(getClass(), "customerPlacesOrder");
    rule.starting(desc);
    rule.context().enterMethod(new MethodSignature("Service", "doWork", List.of()));
    rule.context().exitMethodWithReturn("ok");

    rule.failed(new AssertionError("expected"), desc);
    rule.finished(desc);

    var output = out.toString();
    assertThat(output).contains("Scenario: Customer places order");
    assertThat(output).contains("Service.doWork");
  }

  @Test
  void failedDoesNothingWhenTraceIsEmpty() {
    var rule = new NarrativeTraceRule();
    var out = new ByteArrayOutputStream();
    rule.setOut(new PrintStream(out));
    var desc = Description.createTestDescription(getClass(), "test");
    rule.starting(desc);

    rule.failed(new AssertionError("expected"), desc);
    rule.finished(desc);

    assertThat(out.toString()).isEmpty();
  }

  @Test
  void failedTestPrintsTheDeltaAgainstLastGreenInsteadOfTheFullTrace(@TempDir Path tempDir) {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      var rule = new NarrativeTraceRule();
      rule.setOut(new PrintStream(new ByteArrayOutputStream()));
      rule.setMermaidRenderer(tree -> "sequenceDiagram");
      rule.setPlantumlRenderer(tree -> "@startuml\n@enduml");
      var desc = Description.createTestDescription("com.example.OrderTest", "customerPlacesOrder");

      recordGreenBaselineRun(rule, desc);
      var output = recordGrownFailingRun(rule, desc);

      assertThat(output).contains("Changed since last green (+1 call Ledger.record):");
      assertThat(output).contains("+  - Ledger.record()");
      assertThat(output).doesNotContain("Execution trace:");
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }
  }

  @Test
  void approvalModeFailsUnapprovedScenarioUntilPromoted(@TempDir Path narrativesDir)
      throws Exception {
    System.setProperty("narrativetrace.approval", "true");
    System.setProperty("narrativetrace.approvedDir", narrativesDir.toString());
    try {
      var rule = new NarrativeTraceRule();
      rule.setOut(new PrintStream(new ByteArrayOutputStream()));
      var desc = Description.createTestDescription("com.example.OrderTest", "customerPlacesOrder");

      recordPassingScenario(rule, desc);
      assertThatThrownBy(() -> rule.finished(desc))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("No approved narrative");
      assertThat(narrativesDir.resolve("OrderTest/customer_places_order.received.nt")).exists();

      ai.narrativetrace.core.output.NarrativeApproval.promoteReceived(narrativesDir);

      recordPassingScenario(rule, desc);
      rule.finished(desc);
      assertThat(narrativesDir.resolve("OrderTest/customer_places_order.received.nt"))
          .doesNotExist();
    } finally {
      System.clearProperty("narrativetrace.approval");
      System.clearProperty("narrativetrace.approvedDir");
    }
  }

  @Test
  void approvalSkipsScenariosWithoutAnyTrace(@TempDir Path narrativesDir) {
    System.setProperty("narrativetrace.approval", "true");
    System.setProperty("narrativetrace.approvedDir", narrativesDir.toString());
    try {
      var rule = new NarrativeTraceRule();
      rule.setOut(new PrintStream(new ByteArrayOutputStream()));
      var desc = Description.createTestDescription("com.example.OrderTest", "noTracedCalls");
      rule.starting(desc);

      rule.finished(desc);

      assertThat(narrativesDir.resolve("OrderTest")).doesNotExist();
    } finally {
      System.clearProperty("narrativetrace.approval");
      System.clearProperty("narrativetrace.approvedDir");
    }
  }

  @Test
  void approvalResolvesTheLoadedTestClassName(@TempDir Path narrativesDir) {
    System.setProperty("narrativetrace.approval", "true");
    System.setProperty("narrativetrace.approvedDir", narrativesDir.toString());
    try {
      var rule = new NarrativeTraceRule();
      rule.setOut(new PrintStream(new ByteArrayOutputStream()));
      var desc = Description.createTestDescription(getClass(), "customerPlacesOrder");
      recordPassingScenario(rule, desc);

      assertThatThrownBy(() -> rule.finished(desc)).isInstanceOf(AssertionError.class);

      assertThat(narrativesDir.resolve("NarrativeTraceRuleTest/customer_places_order.received.nt"))
          .exists();
    } finally {
      System.clearProperty("narrativetrace.approval");
      System.clearProperty("narrativetrace.approvedDir");
    }
  }

  @Test
  void approvalWrapsBaselineIoFailuresAsUnchecked(@TempDir Path narrativesDir) throws Exception {
    System.setProperty("narrativetrace.approval", "true");
    System.setProperty("narrativetrace.approvedDir", narrativesDir.toString());
    try {
      Files.createDirectories(narrativesDir.resolve("OrderTest/customer_places_order.approved.nt"));
      var rule = new NarrativeTraceRule();
      rule.setOut(new PrintStream(new ByteArrayOutputStream()));
      var desc = Description.createTestDescription("com.example.OrderTest", "customerPlacesOrder");
      recordPassingScenario(rule, desc);

      assertThatThrownBy(() -> rule.finished(desc))
          .isInstanceOf(java.io.UncheckedIOException.class)
          .hasMessageContaining("could not access the baseline");
    } finally {
      System.clearProperty("narrativetrace.approval");
      System.clearProperty("narrativetrace.approvedDir");
    }
  }

  @Test
  void linkedRuleAccumulatesNothingWhenTraceIsEmpty() {
    var classRule = new NarrativeTraceClassRule();
    var rule = classRule.testRule();
    rule.setOut(new PrintStream(new ByteArrayOutputStream()));
    var desc = Description.createTestDescription(getClass(), "noTracedCalls");
    rule.starting(desc);

    rule.finished(desc);

    assertThat(classRule.accumulatedTraces()).isEmpty();
  }

  private static void recordPassingScenario(NarrativeTraceRule rule, Description desc) {
    rule.starting(desc);
    rule.context().enterMethod(new MethodSignature("Service", "doWork", List.of()));
    rule.context().exitMethodWithReturn("ok");
  }

  private static void recordGreenBaselineRun(NarrativeTraceRule rule, Description desc) {
    rule.starting(desc);
    rule.context().enterMethod(new MethodSignature("Service", "doWork", List.of()));
    rule.context().exitMethodWithReturn("ok");
    rule.finished(desc);
  }

  private static String recordGrownFailingRun(NarrativeTraceRule rule, Description desc) {
    rule.starting(desc);
    rule.context().enterMethod(new MethodSignature("Service", "doWork", List.of()));
    rule.context().enterMethod(new MethodSignature("Ledger", "record", List.of()));
    rule.context().exitMethodWithReturn("recorded");
    rule.context().exitMethodWithReturn("ok");
    var out = new ByteArrayOutputStream();
    rule.setOut(new PrintStream(out));
    rule.failed(new AssertionError("expected"), desc);
    rule.finished(desc);
    return out.toString();
  }

  @Test
  void finishedWritesTraceFileWhenOutputEnabled(@TempDir Path tempDir) throws Exception {
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      var rule = new NarrativeTraceRule();
      var out = new ByteArrayOutputStream();
      rule.setOut(new PrintStream(out));
      rule.setMermaidRenderer(tree -> "sequenceDiagram");
      rule.setPlantumlRenderer(tree -> "@startuml\n@enduml");
      var desc = Description.createTestDescription("com.example.OrderTest", "customerPlacesOrder");
      rule.starting(desc);
      rule.context().enterMethod(new MethodSignature("Service", "doWork", List.of()));
      rule.context().exitMethodWithReturn("ok");

      rule.finished(desc);

      var file = tempDir.resolve("traces/OrderTest/customer_places_order.md");
      assertThat(file).exists();
      assertThat(Files.readString(file)).contains("Service.doWork");
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }
  }

  @Test
  void finishedDoesNotWriteWhenOutputDisabled() {
    System.clearProperty("narrativetrace.output");
    var rule = new NarrativeTraceRule();
    var out = new ByteArrayOutputStream();
    rule.setOut(new PrintStream(out));
    var desc = Description.createTestDescription("com.example.OrderTest", "test");
    rule.starting(desc);
    rule.context().enterMethod(new MethodSignature("Service", "doWork", List.of()));
    rule.context().exitMethodWithReturn("ok");

    rule.finished(desc);

    assertThat(out.toString()).isEmpty();
  }

  @Test
  void finishedSafeWhenContextIsNull() {
    var rule = new NarrativeTraceRule();
    var desc = Description.createTestDescription(getClass(), "test");

    rule.finished(desc);
    // no exception
  }

  @Test
  void writeTraceFileCatchesIoError(@TempDir Path tempDir) throws Exception {
    // Create a file where the traces directory should be, forcing IOException
    Files.writeString(tempDir.resolve("traces"), "blocker");
    System.setProperty("narrativetrace.output", "true");
    System.setProperty("narrativetrace.outputDir", tempDir.toString());
    try {
      var rule = new NarrativeTraceRule();
      var out = new ByteArrayOutputStream();
      var errStream = new ByteArrayOutputStream();
      rule.setOut(new PrintStream(out));
      rule.setErr(new PrintStream(errStream));
      var desc = Description.createTestDescription("com.example.Test", "test");
      rule.starting(desc);
      rule.context().enterMethod(new MethodSignature("Service", "doWork", List.of()));
      rule.context().exitMethodWithReturn("ok");

      rule.finished(desc);

      assertThat(errStream.toString()).contains("Failed to write trace file:");
    } finally {
      System.clearProperty("narrativetrace.output");
      System.clearProperty("narrativetrace.outputDir");
    }
  }

  @Test
  void finishedPrintsTemplateWarningsWhenUnresolvedPlaceholders() {
    var rule = new NarrativeTraceRule();
    var out = new ByteArrayOutputStream();
    rule.setOut(new PrintStream(out));
    var desc = Description.createTestDescription(getClass(), "placeOrder");
    rule.starting(desc);
    rule.context()
        .enterMethod(
            new MethodSignature(
                "OrderService", "placeOrder", List.of(), "Placing order for {custmerId}", null));
    rule.context().exitMethodWithReturn("ok");

    rule.finished(desc);

    var output = out.toString();
    assertThat(output).contains("Unresolved template placeholder");
    assertThat(output).contains("OrderService.placeOrder");
    assertThat(output).contains("{custmerId}");
  }

  @Test
  void finishedNoWarningsForCleanTrace() {
    var rule = new NarrativeTraceRule();
    var out = new ByteArrayOutputStream();
    rule.setOut(new PrintStream(out));
    var desc = Description.createTestDescription(getClass(), "placeOrder");
    rule.starting(desc);
    rule.context()
        .enterMethod(
            new MethodSignature(
                "OrderService", "placeOrder", List.of(), "Placing order for C-123", null));
    rule.context().exitMethodWithReturn("ok");

    rule.finished(desc);

    assertThat(out.toString()).isEmpty();
  }

  @Test
  void isOutputEnabledReadsSysProp() {
    System.setProperty("narrativetrace.output", "true");
    try {
      assertThat(NarrativeTraceRule.isOutputEnabled()).isTrue();
    } finally {
      System.clearProperty("narrativetrace.output");
    }
    assertThat(NarrativeTraceRule.isOutputEnabled()).isFalse();
  }
}
