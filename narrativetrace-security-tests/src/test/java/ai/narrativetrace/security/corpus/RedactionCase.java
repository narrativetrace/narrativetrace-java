/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

import java.util.Map;

/**
 * One row of {@code redaction.json}: a sensitive field name, or a sensitive value shape.
 *
 * <p>INTENT: The two redaction axes are one corpus, because a runtime that implements the name
 * deny-list and forgets the value shapes has half a control and no way to notice. A row names
 * either a field ({@link #name} plus the {@link #canary} planted behind it) or a value ({@link
 * #value}, which is its own canary because the shape <em>is</em> the secret), and {@link #expect}
 * says which way the assertion runs.
 *
 * <p><b>@llmNote</b> The false-positive rows are not decoration. A deny-list is only as good as the
 * field it does <em>not</em> blank: {@code circuitBreaker} must survive {@code cuit} and {@code
 * chosenHash} must survive {@code senha}, or teams switch the default off and lose everything
 * rather than one field.
 *
 * @param id stable kebab-case identifier, quoted by a failing assertion so the case is findable
 * @param description what breaks, not what the bytes are
 * @param name the field name for a name case, {@code null} for a value case
 * @param value the value for a value case, {@code null} for a name case
 * @param canary the string planted behind {@link #name}; {@code null} for a value case
 * @param expect {@code "redacted"} or {@code "visible"}
 */
public record RedactionCase(
    String id, String description, String name, String value, String canary, String expect) {

  /** Whether the canary must appear in no byte of any output. */
  public boolean expectsRedaction() {
    return "redacted".equals(expect);
  }

  /** Whether this row names a field rather than carrying a bare value. */
  public boolean isName() {
    return name != null;
  }

  /**
   * The string the oracle looks for.
   *
   * @return the canary for a name case; the value itself for a value case
   */
  public String secret() {
    return isName() ? canary : value;
  }

  /**
   * The object to render: the value alone, or a one-entry map under the sensitive field name.
   *
   * <p><b>@llmNote</b> A {@code Map} is the vehicle for name cases because a record component has
   * to be a compile-time identifier and these names are data — including two spellings of the same
   * Spanish word that differ only by Unicode normalization form.
   *
   * @return the graph to hand the renderer
   */
  public Object payload() {
    return isName() ? Map.of(name, canary) : value;
  }

  @Override
  public String toString() {
    return id;
  }
}
