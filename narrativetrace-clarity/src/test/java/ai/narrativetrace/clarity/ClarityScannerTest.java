/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClarityScannerTest {

  @Test
  void scanReturnsResultForEachClass() {
    var scanner = new ClarityScanner();
    var results = scanner.scan(List.of(SampleService.class));

    assertThat(results).containsKey("SampleService");
    assertThat(results.get("SampleService").overallScore()).isBetween(0.0, 1.0);
  }

  @Test
  void scanMultipleClasses() {
    var scanner = new ClarityScanner();
    var results = scanner.scan(List.of(SampleService.class, InventoryService.class));

    assertThat(results).containsKeys("SampleService", "InventoryService");
  }

  @Test
  void scanSkipsClassesWithNoPublicMethods() {
    var scanner = new ClarityScanner();
    var results = scanner.scan(List.of(PrivateOnly.class));

    assertThat(results).isEmpty();
  }

  @Test
  void scanSkipsObjectMethods() {
    var scanner = new ClarityScanner();
    var results = scanner.scan(List.of(SampleService.class));

    var result = results.get("SampleService");
    assertThat(result.issues().stream().map(ClarityIssue::element))
        .doesNotContain("SampleService.toString", "SampleService.hashCode", "SampleService.equals");
  }

  @Test
  void scanEmptyListReturnsEmptyMap() {
    var scanner = new ClarityScanner();
    var results = scanner.scan(List.of());

    assertThat(results).isEmpty();
  }

  @Test
  void scanPathLoadsClassesFromDirectory(@TempDir Path tempDir) throws Exception {
    compileToDir(tempDir);
    var scanner = new ClarityScanner();
    var results = scanner.scan(tempDir);

    assertThat(results).isNotEmpty();
  }

  @Test
  void scanPathSkipsEmptyDirectory(@TempDir Path tempDir) throws IOException {
    var scanner = new ClarityScanner();
    var results = scanner.scan(tempDir);

    assertThat(results).isEmpty();
  }

  private void compileToDir(Path dir) throws Exception {
    // Copy a compiled .class file from the test classpath into the temp dir
    var className = SampleService.class.getName();
    var classFile = className.replace('.', '/') + ".class";
    try (var is = getClass().getClassLoader().getResourceAsStream(classFile)) {
      assertThat(is).isNotNull();
      var target = dir.resolve(classFile);
      Files.createDirectories(target.getParent());
      Files.copy(is, target);
    }
  }

  @Test
  void mainWritesScanSpecificFilenames(@TempDir Path tempDir) throws Exception {
    var classesDir = tempDir.resolve("classes");
    compileToDir(classesDir);
    var outputDir = tempDir.resolve("output");

    ClarityScannerMain.main(
        new String[] {
          "--classes-dir", classesDir.toString(),
          "--output-dir", outputDir.toString()
        });

    assertThat(outputDir.resolve("clarity-scan-report.md")).exists();
    assertThat(outputDir.resolve("clarity-scan-results.json")).exists();
  }

  @Test
  void mainNeverTouchesTestRunArtifactFilenames(@TempDir Path tempDir) throws Exception {
    var classesDir = tempDir.resolve("classes");
    compileToDir(classesDir);
    var outputDir = tempDir.resolve("output");
    Files.createDirectories(outputDir);
    Files.writeString(outputDir.resolve("clarity-results.json"), "{\"from\":\"test-run\"}");
    Files.writeString(outputDir.resolve("clarity-report.md"), "# from test run");

    ClarityScannerMain.main(
        new String[] {
          "--classes-dir", classesDir.toString(),
          "--output-dir", outputDir.toString()
        });

    assertThat(outputDir.resolve("clarity-results.json"))
        .content()
        .isEqualTo("{\"from\":\"test-run\"}");
    assertThat(outputDir.resolve("clarity-report.md")).content().isEqualTo("# from test run");
  }

  @Test
  void mainDefaultsFormatToBoth(@TempDir Path tempDir) throws Exception {
    var classesDir = tempDir.resolve("classes");
    compileToDir(classesDir);
    var outputDir = tempDir.resolve("output");

    ClarityScannerMain.main(
        new String[] {
          "--classes-dir", classesDir.toString(),
          "--output-dir", outputDir.toString(),
          "--format", "both"
        });

    assertThat(outputDir.resolve("clarity-scan-report.md")).exists();
    assertThat(outputDir.resolve("clarity-scan-results.json")).exists();
  }

  @Test
  void mainFormatMdWritesOnlyMarkdown(@TempDir Path tempDir) throws Exception {
    var classesDir = tempDir.resolve("classes");
    compileToDir(classesDir);
    var outputDir = tempDir.resolve("output");

    ClarityScannerMain.main(
        new String[] {
          "--classes-dir", classesDir.toString(),
          "--output-dir", outputDir.toString(),
          "--format", "md"
        });

    assertThat(outputDir.resolve("clarity-scan-report.md")).exists();
    assertThat(outputDir.resolve("clarity-scan-results.json")).doesNotExist();
  }

  @Test
  void mainFormatJsonWritesOnlyJson(@TempDir Path tempDir) throws Exception {
    var classesDir = tempDir.resolve("classes");
    compileToDir(classesDir);
    var outputDir = tempDir.resolve("output");

    ClarityScannerMain.main(
        new String[] {
          "--classes-dir", classesDir.toString(),
          "--output-dir", outputDir.toString(),
          "--format", "json"
        });

    assertThat(outputDir.resolve("clarity-scan-report.md")).doesNotExist();
    assertThat(outputDir.resolve("clarity-scan-results.json")).exists();
  }

  @Test
  void invalidFormatThrowsIllegalArgument(@TempDir Path tempDir) throws Exception {
    var classesDir = tempDir.resolve("classes");
    compileToDir(classesDir);
    var outputDir = tempDir.resolve("output");

    assertThatThrownBy(() -> ClarityScannerMain.run(classesDir, outputDir, "xml"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("xml");
  }

  @Test
  void scanCapturesParametersFromMethods() {
    var scanner = new ClarityScanner();
    var results = scanner.scan(List.of(SampleService.class));
    var result = results.get("SampleService");
    // SampleService has methods with parameters; parameterNameScore should reflect those
    // If buildParams returned empty, parameterNameScore would be 1.0 (no params → default)
    // With actual params, it should be < 1.0 or specific based on param names
    assertThat(result.parameterNameScore()).isLessThan(1.0);
  }

  @Test
  void scanExcludesObjectMethodsFromAnalysis() {
    var scanner = new ClarityScanner();
    // ClassWithToString has toString() which is an Object method → should be skipped
    var results = scanner.scan(List.of(ClassWithToString.class));
    var result = results.get("ClassWithToString");
    // Only domainMethod should be analyzed, not toString
    assertThat(result.methodNameScore()).isGreaterThan(0.0);
  }

  @Test
  void scanSkipsPrivateNestedTypes() {
    var scanner = new ClarityScanner();
    var results = scanner.scan(List.of(PrivatePlannerStep.class));

    assertThat(results).isEmpty();
  }

  @SuppressWarnings("unused")
  private record PrivatePlannerStep(String stepName) {
    public void executeStep(String planId) {}
  }

  @Test
  void scanSkipsAnonymousAndLocalClasses() {
    var anonymous =
        new Runnable() {
          @Override
          public void run() {}
        };
    class LocalHelper {
      @SuppressWarnings("unused")
      public void computeTotals(String batchId) {}
    }
    var scanner = new ClarityScanner();
    var results = scanner.scan(List.of(anonymous.getClass(), LocalHelper.class));

    assertThat(results).isEmpty();
  }

  @Test
  void scanScoresRecordAccessorsAsNouns() {
    var scanner = new ClarityScanner();
    var results = scanner.scan(List.of(TravelExpense.class));

    // Accessor names are the component nouns; they must be scored on the noun
    // rubric (description/payer -> 0.80 each), not as verb-less methods (0.50).
    assertThat(results.get("TravelExpense").methodNameScore()).isEqualTo(0.80);
  }

  @SuppressWarnings("unused")
  public record TravelExpense(String description, String payer) {}

  @Test
  void scanFlagsVagueRecordComponentWithNounSuggestion() {
    var scanner = new ClarityScanner();
    var results = scanner.scan(List.of(UnnamedPayload.class));

    var issues = results.get("UnnamedPayload").issues();
    assertThat(issues)
        .anySatisfy(
            issue -> {
              assertThat(issue.category()).isEqualTo("property-name");
              assertThat(issue.element()).isEqualTo("UnnamedPayload.data");
              assertThat(issue.suggestion()).contains("customerId");
              assertThat(issue.severity()).isEqualTo(ClarityIssue.Severity.HIGH);
            });
  }

  @SuppressWarnings("unused")
  public record UnnamedPayload(String data) {}

  @Test
  void scanNeverSuggestsVerbCollocationsForRecordComponents() {
    var scanner = new ClarityScanner();
    var results = scanner.scan(List.of(PurchaseSnapshot.class));

    // customerOrder is a data property, not an action — "fulfillOrder, placeOrder…"
    // rename suggestions would be nonsense for a record component.
    var issues = results.get("PurchaseSnapshot").issues();
    assertThat(issues).noneSatisfy(issue -> assertThat(issue.category()).isEqualTo("collocation"));
  }

  @SuppressWarnings("unused")
  public record PurchaseSnapshot(String customerOrder) {}

  @Test
  void scanKeepsVerbRubricForCustomRecordMethods() {
    var scanner = new ClarityScanner();
    var results = scanner.scan(List.of(LedgerEntry.class));

    // The accessor set covers generated accessors only — a hand-written method on
    // a record is still an action and keeps the verb+noun standard.
    var issues = results.get("LedgerEntry").issues();
    assertThat(issues)
        .anySatisfy(
            issue -> {
              assertThat(issue.category()).isEqualTo("method-name");
              assertThat(issue.element()).isEqualTo("LedgerEntry.process");
            });
  }

  @SuppressWarnings("unused")
  public record LedgerEntry(String payer) {
    public void process() {}
  }

  @Test
  void scanSkipsSyntheticLambdaClasses() {
    Runnable lambda = () -> {};
    var scanner = new ClarityScanner();
    var results = scanner.scan(List.of(lambda.getClass()));

    assertThat(results).isEmpty();
  }

  @SuppressWarnings("unused")
  public static class SampleService {
    public void placeOrder(String orderId) {}

    public String findCustomer(String customerId) {
      return "";
    }
  }

  @SuppressWarnings("unused")
  public static class InventoryService {
    public boolean checkStock(String productId) {
      return true;
    }
  }

  @SuppressWarnings("unused")
  static class PrivateOnly {
    private void secretMethod() {}
  }

  @SuppressWarnings("unused")
  public static class ClassWithToString {
    public void domainMethod() {}

    @Override
    public String toString() {
      return "test";
    }
  }

  @SuppressWarnings("unused")
  public static class OnlyObjectMethods {
    @Override
    public String toString() {
      return "test";
    }

    @Override
    public int hashCode() {
      return 42;
    }

    @Override
    public boolean equals(Object obj) {
      return false;
    }
  }

  @Test
  void scanClassWithOnlyObjectMethodsReturnsEmpty() {
    var scanner = new ClarityScanner();
    // OnlyObjectMethods only has toString, hashCode, equals → all Object methods → skipped
    // No public non-Object methods → nodes list is empty → class is skipped
    var results = scanner.scan(List.of(OnlyObjectMethods.class));
    assertThat(results).doesNotContainKey("OnlyObjectMethods");
  }

  @Test
  void scanClassWithToStringExcludesObjectMethods() {
    var scanner = new ClarityScanner();
    var results = scanner.scan(List.of(ClassWithToString.class));
    var result = results.get("ClassWithToString");
    // Only "domainMethod" should be analyzed (1 method), not "toString"
    // If isObjectMethod mutation returns false, "toString" would also be analyzed
    // We can verify by checking that no issue references "toString"
    assertThat(result.issues().stream().map(ClarityIssue::element))
        .noneMatch(e -> e.contains("toString"));
  }
}
