/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import ai.narrativetrace.api.event.RenderedValue;
import java.time.Instant;
import java.util.ArrayList;

/**
 * Field-level difference between two structured renderings of the same entity.
 *
 * <p>INTENT: When a captured value reappears inside one trace slightly changed — the multi-currency
 * case: a ledger returns an expense at {@code 100.00 USD}, the calculator receives it normalized to
 * {@code 92.00 EUR} — a second full render hides the one field that moved inside a ~300-character
 * blob. This composes the compact form {@code {amount: 100.0→92.0, currency: "USD"→"EUR"}} that
 * {@link ValueReferenceIndex} appends to the reference label, so the change prints AS a diff.
 *
 * <p><b>@llmNote</b> The delta is computed from the two <em>structured</em> trees and formats only
 * scalar leaves. It never reconstructs a flat render from a structured value: the flat and
 * structured channels are captured independently ({@code ValueRenderer.render} / {@code
 * renderStructured}) and deriving one from the other is the parallel-path divergence class this
 * codebase has already paid for twice. Any difference that is not a scalar-to-scalar field change
 * therefore yields {@code null}, and the caller falls back to the full flat render.
 *
 * <p><b>@edgeCase</b> {@link RenderedValue.DoubleVal} carries a {@code double}, so a {@code
 * BigDecimal} captured as {@code 100.00} formats here as {@code 100.0} — the trailing scale is lost
 * at capture, not at render. The full render on the reference line still shows the original text.
 */
final class ValueDelta {

  /** Longest string value shown on either side of a field change before it is elided. */
  private static final int MAX_SCALAR_LENGTH = 60;

  private ValueDelta() {}

  /**
   * Composes the delta of {@code changed} against {@code reference}, or {@code null} when the pair
   * cannot be expressed as a scalar field diff — a different type, a different field set, no
   * difference at all, or a changed field that is itself structured.
   *
   * @param reference the structured form already emitted in full in this document
   * @param changed the structured form of the later, differing emission
   * @return the braced field list, e.g. {@code {amount: 100.0→92.0}}, or {@code null}
   */
  static String between(RenderedValue reference, RenderedValue changed) {
    if (!(reference instanceof RenderedValue.ObjectVal from)
        || !(changed instanceof RenderedValue.ObjectVal to)
        || !from.typeName().equals(to.typeName())
        || !from.fields().keySet().equals(to.fields().keySet())) {
      return null;
    }
    var parts = new ArrayList<String>();
    for (var field : from.fields().keySet()) {
      var before = from.fields().get(field);
      var after = to.fields().get(field);
      if (before.equals(after)) {
        continue;
      }
      var change = scalarChange(before, after);
      if (change == null) {
        return null;
      }
      parts.add(field + ": " + change);
    }
    var delta = parts.isEmpty() ? null : "{" + String.join(", ", parts) + "}";
    assert delta == null || delta.startsWith("{") && delta.endsWith("}") && delta.contains("→")
        : "a non-null delta is a braced, non-empty list of field changes: " + delta;
    return delta;
  }

  private static String scalarChange(RenderedValue before, RenderedValue after) {
    var from = scalar(before);
    var to = scalar(after);
    return from == null || to == null ? null : from + "→" + to;
  }

  /**
   * Formats a scalar leaf the way the flat renderer prints it — a string quoted and control-escaped
   * — or {@code null} for a structured leaf, which has no unambiguous one-line form.
   */
  private static String scalar(RenderedValue value) {
    if (value instanceof RenderedValue.StringVal s) {
      return "\"" + cap(ControlEscape.sanitize(s.value())) + "\"";
    }
    return bareScalar(value);
  }

  /**
   * The unquoted scalars: numbers, booleans, {@code null}, and instants in ISO-8601. {@code null}
   * for {@link RenderedValue.ObjectVal} and {@link RenderedValue.ListVal} — a nested object or list
   * is what makes a change inexpressible as a one-line diff.
   */
  private static String bareScalar(RenderedValue value) {
    if (value instanceof RenderedValue.LongVal l) {
      return Long.toString(l.value());
    }
    if (value instanceof RenderedValue.DoubleVal d) {
      return Double.toString(d.value());
    }
    if (value instanceof RenderedValue.BooleanVal b) {
      return Boolean.toString(b.value());
    }
    if (value instanceof RenderedValue.InstantVal i) {
      return Instant.ofEpochMilli(i.epochMillis()).toString();
    }
    return value instanceof RenderedValue.NullVal ? "null" : null;
  }

  private static String cap(String text) {
    return text.length() > MAX_SCALAR_LENGTH ? text.substring(0, MAX_SCALAR_LENGTH) + "…" : text;
  }
}
