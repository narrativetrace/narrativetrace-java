/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.slf4j;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanContext;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.event.TraceOutcome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

/**
 * Proves {@link Slf4jTraceEventListener} narrates on slf4j-api 1.7.x, not only 2.x.
 *
 * <p>Runs in the {@code slf4j1CompatTest} task (see the module's {@code build.gradle.kts}), whose
 * runtime classpath carries slf4j-api 1.7.36 and slf4j-simple 1.7.36 instead of the 2.0.16 this
 * module compiles against. A listener that calls the fluent {@code Logger.atLevel(...)} API — added
 * in slf4j-api 2.0 — throws {@link NoSuchMethodError} on that classpath instead of failing an
 * assertion, so a regression here fails loudly rather than silently narrating nothing.
 */
class Slf4j17CompatibilityTest {

  @Test
  void entryEventNarratesOnSlf4j17() throws IOException {
    var listener =
        new Slf4jTraceEventListener(Map.of(Slf4jTraceEventListener.EventType.ENTRY, Level.DEBUG));
    var signature = new MethodSignature("Compat17Entry", "run", List.of());

    listener.accept(new TraceEvent.EnterEvent(rootSpanContext(), System.nanoTime(), signature));

    assertThat(lineContaining("→ Compat17Entry.run()")).contains("DEBUG");
  }

  @Test
  void returnEventNarratesOnSlf4j17() throws IOException {
    var listener =
        new Slf4jTraceEventListener(Map.of(Slf4jTraceEventListener.EventType.RETURN, Level.INFO));
    var event =
        new TraceEvent.ExitEvent(
            rootSpanContext(), System.nanoTime(), new TraceOutcome.Returned("\"compat17\""), null);

    listener.accept(event);

    assertThat(lineContaining("← returned: \"compat17\"")).contains("INFO");
  }

  @Test
  void exceptionEventNarratesOnSlf4j17() throws IOException {
    var listener =
        new Slf4jTraceEventListener(
            Map.of(Slf4jTraceEventListener.EventType.EXCEPTION, Level.ERROR));
    var event =
        new TraceEvent.ExitEvent(
            rootSpanContext(),
            System.nanoTime(),
            new TraceOutcome.Threw(new IllegalStateException("compat17-exception")),
            null);

    listener.accept(event);

    assertThat(lineContaining("!! IllegalStateException: compat17-exception")).contains("ERROR");
  }

  @Test
  void forkEventNarratesOnSlf4j17() throws IOException {
    var listener =
        new Slf4jTraceEventListener(Map.of(Slf4jTraceEventListener.EventType.ENTRY, Level.WARN));

    listener.accept(new TraceEvent.ForkCreatedEvent("compat17-fork", System.nanoTime()));

    assertThat(lineContaining("⑂ fork group created [groupId: compat17-fork]")).contains("WARN");
  }

  private static SpanContext rootSpanContext() {
    return SpanContext.builder(SpanIdGenerator.traceId(), SpanIdGenerator.spanId()).build();
  }

  private static String lineContaining(String marker) throws IOException {
    var logFile = Path.of(System.getProperty("org.slf4j.simpleLogger.logFile"));
    return Files.readAllLines(logFile).stream()
        .filter(line -> line.contains(marker))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no log line contains: " + marker));
  }
}
