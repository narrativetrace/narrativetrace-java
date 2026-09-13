/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

/**
 * Metadata supplied by callers when rendering a trace as a standalone document.
 *
 * <p>INTENT: Keep scenario and overall result outside the trace tree itself so the same trace can
 * be rendered in different contexts.
 *
 * @param runName the enclosing test-suite run's three-word phrase ({@code
 *     ai.narrativetrace.core.output.RunIdentity#name()}), or {@code null} when this document is not
 *     rendered inside a run an integration tracks (2026-09-13 ruling, item 2) — carried into the
 *     Markdown frontmatter's {@code run:} field, never into anything the structural {@code .nt}
 *     artifact or a delta computation reads
 */
public record TraceMetadata(String scenario, ScenarioResult result, String runName) {

  public TraceMetadata {
    if (scenario == null) {
      throw new IllegalArgumentException("scenario must not be null");
    }
    if (result == null) {
      throw new IllegalArgumentException("result must not be null");
    }
  }

  /** No enclosing run — a caller rendering a document outside a tracked test-suite execution. */
  public TraceMetadata(String scenario, ScenarioResult result) {
    this(scenario, result, null);
  }

  /**
   * Compatibility constructor for call sites that still pass the result as text.
   *
   * @deprecated Pass a {@link ScenarioResult} directly. Accepts either the wire spelling ({@code
   *     success}/{@code error}) or the display spelling ({@code PASSED}/{@code FAILED}) and rejects
   *     anything else, so a value that the canonical schema would refuse fails here instead of
   *     reaching an artifact.
   */
  @Deprecated(since = "0.2.0")
  public TraceMetadata(String scenario, String result) {
    this(scenario, ScenarioResult.from(result), null);
  }
}
