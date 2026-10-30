/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADV-2026-10-30-5, sibling site — {@code ClarityScanner.scan(Path)} catches {@code
 * ClassNotFoundException} and {@code NoClassDefFoundError} around {@code loadClass}, so a corrupt
 * or newer-release class file ends the scan with a {@code LinkageError} instead of being ignored.
 *
 * <p>INTENT: the class's own promise is "Classes with missing dependencies are silently ignored
 * rather than failing the whole scan", and this scan runs inside {@code clarityScan} — a gate in
 * someone else's build, which the diagnostic may never be the reason to fail. The reproducer in
 * {@code narrativetrace-glossary} ({@code StaticScanUnloadableClassFileTest}) is the same defect at
 * the other site; the two share the clause, so they share the fix.
 */
class ClarityScanUnloadableClassFileTest {

  @TempDir Path classesDir;

  @Test
  @DisplayName("ADV-2026-10-30-5: a corrupt class file is skipped, not raised through the scan")
  void aCorruptClassFileIsSkipped() throws Exception {
    Files.write(classesDir.resolve("Corrupt.class"), "this is not a class file".getBytes("UTF-8"));

    assertThatCode(() -> new ClarityScanner().scan(classesDir)).doesNotThrowAnyException();
  }
}
