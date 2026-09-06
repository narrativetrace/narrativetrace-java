/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TraceFileWriterTest {

  @Test
  void createsParentDirectoriesWhenWriting(@TempDir Path tempDir) throws IOException {
    var writer = new TraceFileWriter();
    var content = "# Trace\n";
    var file = tempDir.resolve("traces/OrderServiceTest/customer_places_order.md");

    assertThat(file.getParent()).doesNotExist();

    writer.write(content, file);

    assertThat(file).exists();
    assertThat(Files.readString(file)).isEqualTo(content);
  }

  @Test
  void doesNotThrowWhenPathHasNoParent() throws IOException {
    var writer = new TraceFileWriter();
    var content = "# Trace\n";
    var file = Path.of("test-trace-" + System.nanoTime() + ".md");

    try {
      writer.write(content, file);
      assertThat(Files.readString(file)).isEqualTo(content);
    } finally {
      Files.deleteIfExists(file);
    }
  }

  /**
   * Regression: content that reaches the writer still holding an unpaired surrogate — narration or
   * an exception message, neither of which passes through {@code ControlEscape} — used to raise
   * {@code UnmappableCharacterException} out of the writer and fail the traced run. Found by the
   * security suite's hostile corpus.
   */
  @Test
  void writesUnencodableContentAsAReplacementRatherThanThrowing(@TempDir Path tempDir)
      throws IOException {
    var writer = new TraceFileWriter();
    var file = tempDir.resolve("trace.md");

    writer.write("before" + (char) 0xd800 + "after", file);

    assertThat(Files.readString(file)).startsWith("before").endsWith("after");
  }

  @Test
  void writesAWellFormedSurrogatePairUnchanged(@TempDir Path tempDir) throws IOException {
    var writer = new TraceFileWriter();
    var monkey = "" + (char) 0xd83d + (char) 0xde48;
    var file = tempDir.resolve("trace.md");

    writer.write("see " + monkey, file);

    assertThat(Files.readString(file)).isEqualTo("see " + monkey);
  }

  @Test
  void writesMarkdownStringToFile(@TempDir Path tempDir) throws IOException {
    var writer = new TraceFileWriter();
    var content = "---\ntype: trace\n---\n\n## Trace: OrderService.placeOrder\n";
    var file = tempDir.resolve("trace.md");

    writer.write(content, file);

    assertThat(file).exists();
    assertThat(Files.readString(file)).isEqualTo(content);
  }
}
