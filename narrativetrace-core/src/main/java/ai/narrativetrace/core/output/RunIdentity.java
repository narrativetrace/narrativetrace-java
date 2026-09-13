/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.core.render.TraceNamer;

/**
 * A test-suite execution's own identity: a W3C-shaped id and the three-word phrase derived from it.
 *
 * <p>INTENT: A trace has a name because a trace id is unreadable; a whole SUITE RUN needed the same
 * thing for a different reason — before this type, nothing named "this execution" at all, so a
 * console footer, a {@code manifest.json}, or a log aggregator query could say "18 scenarios" but
 * never "which 18-scenario run" when two ran back to back. One {@code RunIdentity} is generated
 * once per test-suite execution (2026-09-13 ruling) and threaded explicitly to every place that
 * names the run — never re-derived, never a process-wide singleton a caller cannot vary, which is
 * exactly what makes the "two runs, byte-identical artifacts, different run name" proof possible.
 *
 * <p><b>@llmNote</b> This is deliberately NOT a {@link TraceId}: a run is not a trace, has no
 * spans, and must never be confused with one in an exporter or a schema. It reuses {@link
 * TraceId#generate} only because a run id needs the same shape (32 lowercase hex) and the same
 * generator's entropy — borrowing the primitive, not the concept. {@link TraceNamer} is reused
 * outright: the whole point of ruling item 2 is that a run's phrase and a trace's phrase come from
 * the same three tables, so a reader who has learned to read one learns to read both.
 *
 * <p><b>@llmNote</b> Cross-cutting invariant (ruling item 3): a {@code RunIdentity} must never
 * reach the structural {@code .nt} text, an approved/received trace, an artifact filename, a
 * manifest per-scenario key, or {@link ScenarioDelta} — every call site that computes one of those
 * receives no {@code RunIdentity} parameter at all, so the omission is structural, not a discipline
 * someone has to remember.
 *
 * @param id the run's own W3C-shaped id — 32 lowercase hex characters, unrelated to any trace id
 * @param name the three-word phrase {@link TraceNamer} derives from {@code id}
 */
public record RunIdentity(String id, String name) {

  /** Rejects what cannot name a run. */
  public RunIdentity {
    if (id == null || id.isEmpty()) {
      throw new IllegalArgumentException("id must not be null or empty");
    }
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("name must not be null or empty");
    }
  }

  /**
   * Generates a fresh run identity: a new random id and the phrase derived from it.
   *
   * <p>Call this exactly once per test-suite execution — see the type's own {@code @llmNote} — and
   * pass the single result everywhere a run needs to be named. Calling it twice names two different
   * runs, which is correct when there genuinely are two (see the byte-identity proof), and wrong
   * when a caller wanted the same run twice.
   */
  public static RunIdentity generate() {
    var id = TraceId.generate().value();
    return new RunIdentity(id, TraceNamer.name(id));
  }
}
