/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import ai.narrativetrace.core.render.ControlEscape;

/**
 * Normalises request-context values on their way out to MDC, logs and telemetry backends.
 *
 * <p>INTENT: {@code HttpRoute}, {@code ClientIp}, {@code EnduserId}, {@code SessionId} and {@code
 * TenantId} preserve exactly what the request said — deliberately, because capture is supposed to
 * be faithful. The sinks are not so forgiving. An adversarial review showed all three of route,
 * client IP and end-user id carrying raw newlines into MDC, where a layout that prints MDC without
 * JSON escaping forges a log line a SIEM cannot distinguish from a real one (CWE-117), and into
 * OTel attributes, where an unbounded value is unbounded cardinality.
 *
 * <p><b>@llmNote</b> This is an <em>export</em> concern, not a capture one. ADR-002 keeps capture
 * faithful and does its projection last, so the raw value stays in the model and in the JSON export
 * (which escapes properly on its own) and is normalised at the boundary where the sink's rules
 * apply. One helper rather than five call sites doing it slightly differently — a normaliser that
 * drifts is a forged log line on whichever sink fell behind.
 *
 * <p><b>@edgeCase</b> Applied at two layers on purpose. The HTTP filters normalise what they read
 * off the request, so MDC and the captured context agree; the OTel mapper normalises again on the
 * way out, because a value set programmatically through {@code setRequestContext} never passed
 * through a filter at all.
 */
public final class ContextExport {

  /**
   * Ceiling on an exported context value.
   *
   * <p>Long enough for any real route, address or opaque user identifier, short enough that a
   * hostile {@code X-Forwarded-For} cannot fill a log file or a metric label. 256 rather than the
   * renderer's 200 because a templated route with several path variables is legitimately longer
   * than a rendered value.
   */
  public static final int MAX_LENGTH = 256;

  private static final String ELLIPSIS = "…";

  private ContextExport() {}

  /**
   * The export form of a context value: control characters rendered inert, length capped.
   *
   * @param raw the value as the request supplied it, or {@code null}
   * @return {@code null} for {@code null} input, otherwise a single-line, bounded string
   */
  public static String normalized(String raw) {
    if (raw == null) {
      return null;
    }
    var safe = ControlEscape.sanitize(raw);
    return safe.length() > MAX_LENGTH ? safe.substring(0, MAX_LENGTH) + ELLIPSIS : safe;
  }

  /**
   * The export form of a typed context value, via its {@code toString()}.
   *
   * @param raw a {@code HttpRoute}, {@code ClientIp}, {@code EnduserId}, or {@code null}
   * @return {@code null} when the value or its text is {@code null}, otherwise the normalised text
   */
  public static String normalized(Object raw) {
    return raw == null ? null : normalized(raw.toString());
  }
}
