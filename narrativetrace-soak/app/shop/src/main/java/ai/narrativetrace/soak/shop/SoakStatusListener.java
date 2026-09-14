/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop;

import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusListener;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Writes Logback's own appender status events (rollovers, open/close, errors) to a plain file
 * instead of the console — P2's oracles read roll counts from here, separately from the
 * application's own rolling log. Instantiated by Joran directly from {@code logback-spring.xml}, so
 * it can only see environment state available at JVM start, not Spring's resolved properties;
 * {@code SOAK_LOG_DIR} is read directly for the same reason the appenders' own {@code LOG_DIR}
 * property is.
 */
public class SoakStatusListener implements StatusListener {

  private final Path statusFile;

  public SoakStatusListener() {
    var dir = System.getenv().getOrDefault("SOAK_LOG_DIR", "logs");
    this.statusFile = Path.of(dir, "shop-appender-status.log");
  }

  @Override
  public void addStatusEvent(Status status) {
    try {
      Files.createDirectories(statusFile.getParent());
      try (var writer =
          new PrintWriter(
              Files.newBufferedWriter(
                  statusFile,
                  StandardCharsets.UTF_8,
                  StandardOpenOption.CREATE,
                  StandardOpenOption.APPEND))) {
        writer.printf(
            "%s [%s] %s%n", Instant.now(), status.getEffectiveLevel(), status.getMessage());
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to write appender status file: " + statusFile, e);
    }
  }
}
