/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.glossary.fixtures.OverdraftFixtureService;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GlossaryScannerMainTest {

  @TempDir Path classesDir;
  @TempDir Path glossaryDir;
  @TempDir Path outputDir;

  private final ByteArrayOutputStream console = new ByteArrayOutputStream();
  private PrintStream originalOut;

  @BeforeEach
  void captureConsole() {
    originalOut = System.out;
    System.setOut(new PrintStream(console, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restoreConsole() {
    System.setOut(originalOut);
  }

  private void copyFixtureClassInto(Path target) throws Exception {
    var classFile = OverdraftFixtureService.class.getName().replace('.', '/') + ".class";
    try (var source = getClass().getClassLoader().getResourceAsStream(classFile)) {
      assertThat(source).isNotNull();
      var destination = target.resolve(classFile);
      Files.createDirectories(destination.getParent());
      Files.copy(source, destination);
    }
  }

  @Test
  void writesGlossaryFilesAndUsageReportFromCompiledClasses() throws Exception {
    copyFixtureClassInto(classesDir);

    GlossaryScannerMain.run(classesDir, glossaryDir, outputDir);

    assertThat(glossaryDir.resolve("glossary.json"))
        .exists()
        .content()
        .contains("Opening overdraft account for {customerId}");
    assertThat(glossaryDir.resolve("glossary.md")).exists();
    assertThat(outputDir.resolve("glossary-usage.json")).exists();
    assertThat(console.toString(StandardCharsets.UTF_8)).contains("Vocabulary:");
  }

  @Test
  void anEmptyClassesDirectoryLeavesTheGlossaryUntouched() throws Exception {
    GlossaryScannerMain.run(classesDir, glossaryDir, outputDir);

    assertThat(glossaryDir.resolve("glossary.json")).doesNotExist();
    assertThat(console.toString(StandardCharsets.UTF_8)).contains("No classes found");
  }

  @Test
  void mainParsesArgumentsAndHarvests() throws Exception {
    copyFixtureClassInto(classesDir);

    GlossaryScannerMain.main(
        new String[] {
          "--classes-dir", classesDir.toString(),
          "--glossary-dir", glossaryDir.toString(),
          "--output-dir", outputDir.toString()
        });

    assertThat(glossaryDir.resolve("glossary.json")).exists();
  }

  @Test
  void aMissingClassesDirectoryArgumentIsRejected() {
    var errors = captureErrors();

    var exitCode =
        GlossaryScannerMain.execute(new String[] {"--glossary-dir", glossaryDir.toString()});

    assertThat(exitCode).isEqualTo(1);
    assertThat(errors.get()).contains("--classes-dir is required");
  }

  @Test
  void anUnreadableClassesDirectoryIsReportedAsAFailure() {
    var errors = captureErrors();

    var exitCode =
        GlossaryScannerMain.execute(
            new String[] {
              "--classes-dir", classesDir.resolve("absent").toString(),
              "--glossary-dir", glossaryDir.toString()
            });

    assertThat(exitCode).isEqualTo(1);
    assertThat(errors.get()).contains("Error:");
  }

  @Test
  void omittedPathArgumentsFallBackToTheirDefaults() {
    var args = new String[] {"--classes-dir", "somewhere"};

    assertThat(GlossaryScannerMain.pathArg(args, "--glossary-dir", Path.of(".")))
        .isEqualTo(Path.of("."));
    assertThat(GlossaryScannerMain.pathArg(args, "--classes-dir", Path.of("fallback")))
        .isEqualTo(Path.of("somewhere"));
  }

  private java.util.function.Supplier<String> captureErrors() {
    var errors = new ByteArrayOutputStream();
    var originalErr = System.err;
    System.setErr(new PrintStream(errors, true, StandardCharsets.UTF_8));
    restoreErr = () -> System.setErr(originalErr);
    return () -> errors.toString(StandardCharsets.UTF_8);
  }

  private Runnable restoreErr = () -> {};

  @AfterEach
  void restoreErrorStream() {
    restoreErr.run();
  }
}
