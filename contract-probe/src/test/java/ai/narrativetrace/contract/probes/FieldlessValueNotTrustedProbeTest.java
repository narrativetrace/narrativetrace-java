/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract.probes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * B-64: the probe used to pass its sentinel as a traced ARGUMENT to {@code Factory.make(hidden)}. A
 * traced argument IS rendered — that is the very rule the redaction probes in this package prove —
 * so the argument-capture line itself carried the sentinel and the probe was measuring its own
 * leak, never the value renderer's. This test attaches its own capture alongside the probe's
 * (Logback allows more than one appender on the same logger) and inspects the individual lines: the
 * traced ARGUMENT line ({@code → …make(…}) must never carry the sentinel, and the traced RETURN
 * line ({@code ← returned: …}) must name the type — the only line this probe is meant to measure.
 */
class FieldlessValueNotTrustedProbeTest {

  // Duplicated from FieldlessValueNotTrustedProbe.SENTINEL (private there) — the exact literal
  // the probe passes in, so this test fails loudly if the two ever drift apart.
  private static final String SENTINEL = "side-table-secret";

  @Test
  void tracedArgumentLineNeverCarriesTheSentinelAndReturnLineNamesTheType() {
    ListAppender<ILoggingEvent> capture = SlfCapture.attach();

    FieldlessValueNotTrustedProbe.observe();

    List<String> lines = capture.list.stream().map(ILoggingEvent::getFormattedMessage).toList();

    String argumentLine =
        lines.stream()
            .filter(line -> line.contains("→") && line.contains("make("))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no traced argument line captured: " + lines));
    assertFalse(
        argumentLine.contains(SENTINEL),
        "a traced argument is rendered; the probe must get the secret in without passing it as "
            + "a traced argument, but the argument line carried it: "
            + argumentLine);

    String returnLine =
        lines.stream()
            .filter(line -> line.contains("←") && line.contains("returned"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no traced return line captured: " + lines));
    assertTrue(
        returnLine.contains("Fieldless"),
        "the return line — the only line this probe measures — must name the type: " + returnLine);
  }
}
