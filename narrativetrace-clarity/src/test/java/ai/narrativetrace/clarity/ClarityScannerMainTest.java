/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClarityScannerMainTest {

  @Test
  void invalidFormatRejectedEvenWhenNoClassesAreFound(@TempDir Path tempDir) {
    var classesDir = tempDir.resolve("classes");
    var outputDir = tempDir.resolve("output");

    assertThatThrownBy(() -> invokeRun(classesDir, outputDir, "xml"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("xml");
  }

  @SuppressWarnings({"PMD.AvoidAccessibilityAlteration", "PMD.PreserveStackTrace"})
  private static void invokeRun(Path classesDir, Path outputDir, String format) throws Exception {
    var method =
        ClarityScannerMain.class.getDeclaredMethod("run", Path.class, Path.class, String.class);
    method.setAccessible(true);
    try {
      method.invoke(null, classesDir, outputDir, format);
    } catch (java.lang.reflect.InvocationTargetException e) {
      if (e.getCause() instanceof Exception ex) {
        throw ex;
      }
      throw e;
    }
  }

  @Test
  void missingClassesDirIsReportedAsAFailureStatus() {
    var status = ClarityScannerMain.run(new String[] {}, ProjectVocabularySource.none());

    assertThat(status).isEqualTo(1);
  }

  @Test
  void scanScoresWithTheVocabularyTheSourceResolvesForTheGlossaryDirectory(@TempDir Path tempDir)
      throws Exception {
    var classesDir = tempDir.resolve("classes");
    java.nio.file.Files.createDirectories(classesDir);
    var outputDir = tempDir.resolve("output");
    var seen = new Path[1];

    var status =
        ClarityScannerMain.run(
            new String[] {
              "--classes-dir", classesDir.toString(),
              "--output-dir", outputDir.toString(),
              "--glossary-dir", tempDir.toString()
            },
            dir -> {
              seen[0] = dir;
              return DomainVocabulary.empty();
            });

    assertThat(status).isEqualTo(0);
    assertThat(seen[0]).isEqualTo(tempDir);
  }

  @Test
  void anAbsentGlossaryDirectoryReachesTheSourceAsNull(@TempDir Path tempDir) throws Exception {
    var classesDir = tempDir.resolve("classes");
    java.nio.file.Files.createDirectories(classesDir);
    var seen = new Path[] {tempDir};

    ClarityScannerMain.run(
        new String[] {
          "--classes-dir", classesDir.toString(),
          "--output-dir", tempDir.resolve("output").toString()
        },
        dir -> {
          seen[0] = dir;
          return DomainVocabulary.empty();
        });

    assertThat(seen[0]).isNull();
  }

  @Test
  void rejectsANullVocabularySource() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ClarityScannerMain.run(new String[] {"--classes-dir", "x"}, null))
        .withMessageContaining("vocabularySource");
  }

  @Test
  void aMissingClassesDirectoryFailsTheScanAndSaysWhy(@TempDir Path tempDir) {
    var stderr = new java.io.ByteArrayOutputStream();
    var original = System.err;
    System.setErr(new java.io.PrintStream(stderr, true, java.nio.charset.StandardCharsets.UTF_8));
    try {
      var status =
          ClarityScannerMain.run(
              new String[] {"--classes-dir", tempDir.resolve("absent").toString()},
              ProjectVocabularySource.none());

      assertThat(status).isEqualTo(1);
      assertThat(stderr.toString(java.nio.charset.StandardCharsets.UTF_8)).startsWith("Error: ");
    } finally {
      System.setErr(original);
    }
  }

  @Test
  void aMissingRequiredArgumentSaysWhichOne() {
    var stderr = new java.io.ByteArrayOutputStream();
    var original = System.err;
    System.setErr(new java.io.PrintStream(stderr, true, java.nio.charset.StandardCharsets.UTF_8));
    try {
      ClarityScannerMain.run(new String[] {}, ProjectVocabularySource.none());

      assertThat(stderr.toString(java.nio.charset.StandardCharsets.UTF_8))
          .contains("--classes-dir is required");
    } finally {
      System.setErr(original);
    }
  }

  @Test
  void mainReturnsWithoutTerminatingTheVmOnSuccess(@TempDir Path tempDir) throws Exception {
    var classesDir = tempDir.resolve("classes");
    java.nio.file.Files.createDirectories(classesDir);

    ClarityScannerMain.main(
        new String[] {
          "--classes-dir", classesDir.toString(),
          "--output-dir", tempDir.resolve("output").toString()
        });

    assertThat(tempDir).exists();
  }
}
