/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File-writing sink of the live translation stream: one {@code <traceId>.md} per trace.
 *
 * <p>INTENT: The persistence half of {@link TranslationSubscriber}'s file-writing variant. Each
 * rendered line appends immediately (with a trailing newline), so a translated trace file is
 * tail-able while its trace is still running and is complete once the trace's gaps footer lands.
 *
 * <p><b>@llmNote</b> The output directory must be creatable at construction — a configured
 * destination that cannot exist is a configuration error and fails fast. Per-line IO is
 * best-effort: the first failure is logged at WARN, later ones are dropped silently (a persistently
 * unwritable directory must not turn the log into a failure storm), and no failure ever reaches the
 * pipeline. File stems are trace ids, which {@code TraceId} validates to exactly 32 lowercase hex
 * characters at construction — filesystem-safe by type, no sanitization needed here.
 */
final class TranslationFileSink implements TranslationSubscriber.TraceLineSink {

  private static final Logger LOGGER = LoggerFactory.getLogger(TranslationFileSink.class);

  private final Path outputDir;
  private final AtomicBoolean failureReported = new AtomicBoolean();

  /**
   * @param outputDir directory receiving one {@code <traceId>.md} per trace; created if absent
   * @throws IllegalArgumentException when {@code outputDir} is null or cannot be created
   */
  TranslationFileSink(Path outputDir) {
    if (outputDir == null) {
      throw new IllegalArgumentException("outputDir must not be null");
    }
    try {
      this.outputDir = Files.createDirectories(outputDir);
    } catch (IOException e) {
      throw new IllegalArgumentException("cannot create translation output dir " + outputDir, e);
    }
  }

  @Override
  public void emit(String traceId, String line) {
    var file = outputDir.resolve(traceId + ".md");
    try {
      Files.writeString(file, line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException | UncheckedIOException e) {
      if (failureReported.compareAndSet(false, true)) {
        LOGGER.warn("dropping translated trace lines; cannot write {}", file, e);
      }
    }
  }
}
