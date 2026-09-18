/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

import java.util.List;
import java.util.Map;

/**
 * One row of {@code redaction.json}: a sensitive field name, a sensitive value shape, or one of
 * {@code graphs.json}'s composite shapes replayed through the capture path.
 *
 * <p>INTENT: The two redaction axes are one corpus, because a runtime that implements the name
 * deny-list and forgets the value shapes has half a control and no way to notice. A row names
 * either a field ({@link #name} plus the {@link #canary} planted behind it) or a value ({@link
 * #value}, which is its own canary because the shape <em>is</em> the secret), and {@link #expect}
 * says which way the assertion runs.
 *
 * <p><b>@llmNote</b> A third row shape, {@link #kind}, names one of {@code graphs.json}'s composite
 * builders (a curated {@code toString}, a sensitive map key, a throwing summary) instead of a bare
 * field or value. Unlike a name or value row — which the renderer alone can settle — a {@code kind}
 * row is meaningless replayed through {@link ai.narrativetrace.core.render.ValueRenderer} directly:
 * that door is already covered by {@code graphs.json}'s own rows. What a {@code kind} row in THIS
 * corpus adds is the capture path one layer up — a traced method call, parameter binding and every
 * rendered artifact — which is a materially different place for a redaction decision to go wrong.
 *
 * <p><b>@llmNote</b> The false-positive rows are not decoration. A deny-list is only as good as the
 * field it does <em>not</em> blank: {@code circuitBreaker} must survive {@code cuit} and {@code
 * chosenHash} must survive {@code senha}, or teams switch the default off and lose everything
 * rather than one field.
 *
 * <p><b>@llmNote</b> ADV-2026-09-14-1: a value case's {@link #position} defaults to a bare
 * top-level scalar. Every name case already places its canary behind a field name in a one-entry
 * {@code Map} ({@link #payload()}), so without this field the corpus could only ever put a
 * value-shaped secret where the renderer's value-shape axis was already known to look — never in a
 * {@code Map} KEY position, which is exactly where {@code ValueRenderer.renderMapKey} was found to
 * skip that axis. {@code "mapKey"} places {@link #value} as the key of a one-entry map instead.
 *
 * @param id stable kebab-case identifier, quoted by a failing assertion so the case is findable
 * @param description what breaks, not what the bytes are
 * @param name the field name for a name case, {@code null} otherwise
 * @param value the value for a value case, {@code null} otherwise
 * @param canary the string planted behind {@link #name} or (for a {@link #kind} row) inside the
 *     composite {@link #kind} builds; {@code null} for a value case
 * @param expect {@code "redacted"} or {@code "visible"}
 * @param position {@code "mapKey"} to place a value case's {@link #value} as a map key rather than
 *     rendering it bare; {@code null} (the default) for every other row
 * @param kind the name of one of {@code graphs.json}'s composite builders, or {@code null} for a
 *     name or value row
 */
public record RedactionCase(
    String id,
    String description,
    String name,
    String value,
    String canary,
    String expect,
    String position,
    String kind) {

  private static final String MAP_KEY_POSITION = "mapKey";

  /** Whether the canary must appear in no byte of any output. */
  public boolean expectsRedaction() {
    return "redacted".equals(expect);
  }

  /** Whether this row names a field rather than carrying a bare value or a composite kind. */
  public boolean isName() {
    return name != null;
  }

  /** Whether this row names one of {@code graphs.json}'s composite builders. */
  public boolean isKind() {
    return kind != null;
  }

  /** Whether a value case places {@link #value} as a map key rather than rendering it bare. */
  public boolean isMapKey() {
    return MAP_KEY_POSITION.equals(position);
  }

  /**
   * The string the oracle looks for.
   *
   * @return the canary for a name or kind case; the value itself for a value case
   */
  public String secret() {
    return isName() || isKind() ? canary : value;
  }

  /**
   * The object to render: the value alone, the value as a map key, a one-entry map under the
   * sensitive field name, or the composite {@link #kind} builds around {@link #canary}.
   *
   * <p><b>@llmNote</b> A {@code Map} is the vehicle for name cases because a record component has
   * to be a compile-time identifier and these names are data — including two spellings of the same
   * Spanish word that differ only by Unicode normalization form. A value case opts into the same
   * vehicle, as the KEY rather than the value, via {@link #isMapKey()}. A kind row delegates to
   * {@link HostileGraphs#build}, the same builder {@code graphs.json}'s own rows use, via a {@link
   * GraphCase} that carries only {@link #kind} — the other {@code GraphCase} fields go unused by
   * every kind these four rows name.
   *
   * @return the graph to hand the renderer
   */
  public Object payload() {
    if (isName()) {
      return Map.of(name, canary);
    }
    if (isKind()) {
      return HostileGraphs.build(kindGraphCase(), canary);
    }
    return isMapKey() ? Map.of(value, "visible-value") : value;
  }

  private GraphCase kindGraphCase() {
    return new GraphCase(
        id, description, kind, List.of(), null, null, null, null, "secret-record", 0);
  }

  @Override
  public String toString() {
    return id;
  }
}
