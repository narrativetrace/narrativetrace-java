/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADV-2026-10-30-5 — the static scan catches {@code ClassNotFoundException} and {@code
 * NoClassDefFoundError} around {@code loadClass}, which leaves every other way a class file fails
 * to define uncaught: a corrupt file raises {@code ClassFormatError} and one compiled for a newer
 * release raises {@code UnsupportedClassVersionError}. Both are {@code LinkageError}s, neither is a
 * {@code NoClassDefFoundError}, and both end the whole scan from inside the directory walk.
 *
 * <p>INTENT: the method's declared contract is best-effort over the whole tree — "one that cannot
 * be loaded (a missing transitive dependency) is skipped rather than failing the scan, matching the
 * best-effort contract of the clarity scanner" — and the scan's caller is a Gradle task in someone
 * else's build, so the diagnostic's own failure becomes their build's. A compiled-classes directory
 * is exactly where stale, truncated and newer-release files accumulate: a cancelled compile, a jar
 * unpacked over the tree, a module built with a higher release in a mixed-toolchain repository.
 *
 * <p><b>@llmNote</b> The sibling site is identical and shares the clause, so a fix is a sweep:
 * {@code ClarityScanner.scan(Path)} (narrativetrace-clarity) catches the same two names in the same
 * {@code forEach}, under a JavaDoc making the same promise ("Classes with missing dependencies are
 * silently ignored rather than failing the whole scan"). The honest catch here is {@code
 * LinkageError} alongside {@code ClassNotFoundException}.
 */
class StaticScanUnloadableClassFileTest {

  @TempDir Path classesDir;

  @Test
  @DisplayName("ADV-2026-10-30-5: a corrupt class file is skipped, not raised through the scan")
  void aCorruptClassFileIsSkipped() throws Exception {
    Files.write(classesDir.resolve("Corrupt.class"), "this is not a class file".getBytes("UTF-8"));

    assertThatCode(() -> new GlossaryStaticScanner().scan(classesDir)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName(
      "ADV-2026-10-30-5: a newer-release class file is skipped, not raised through the scan")
  void aClassFileFromANewerReleaseIsSkipped() throws Exception {
    Files.write(classesDir.resolve("FromTheFuture.class"), futureVersionClassFile());

    assertThatCode(() -> new GlossaryStaticScanner().scan(classesDir)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("ADV-2026-10-30-5: a scan of a tree holding one bad file still reports the rest")
  void aScanOverABadFileStillReturnsAResult() throws Exception {
    Files.write(classesDir.resolve("Corrupt.class"), new byte[] {(byte) 0xCA, (byte) 0xFE});

    var trees = new GlossaryStaticScanner().scan(classesDir);

    assertThat(trees).isEmpty();
  }

  /** A class file header whose major version no JVM running this suite can accept. */
  private static byte[] futureVersionClassFile() {
    return new byte[] {
      (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0x00, 0x00, 0x00, (byte) 0xFF
    };
  }
}
