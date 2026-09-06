/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.export;

import java.util.List;

/**
 * Projects a {@link CanonicalEntry} to its AI-safe structural form: Level 1 ("Structure Only") of
 * the AI output ladder (ADR-002).
 *
 * <p>INTENT: The structural artifact is the value-free projection of the same capture the canonical
 * export serializes — developer-authored structure (names, call shape, outcomes, timing, raw
 * narration templates) with every runtime-value field elided. Safety is architectural: value fields
 * do not exist in the output, so neither prompt-injection payloads nor private data can reach an AI
 * consumer.
 *
 * <p><b>@llmNote</b> This projection is deliberately the LAST step before serialization: it
 * consumes fully-valued canonical entries. Higher AI output levels (pseudonymized, selective — Pro
 * tier) replace this per-field policy while reading the same input; never strip values upstream of
 * this seam.
 *
 * <p>Parameter entries keep their names (names are source code, not data) and carry the
 * self-describing {@code [ELIDED]} value, mirroring the {@code [REDACTED]} convention, because the
 * schema requires a string value per parameter. The output remains valid against the schema (1.2).
 *
 * <p><b>Stance for new schema fields</b>: since the {@code toBuilder()} rewrite, fields flow
 * through this projection BY DEFAULT. That is correct for identity-shaped fields (packages,
 * declared types, thread/resource identity, source location, instance hashes — all source or
 * environment identity, never runtime content). Any new VALUE-shaped field must be explicitly
 * nulled here and seeded with hostile content in {@code StructuralProjectionPropertyTest}; classify
 * every schema addition against that property test.
 */
public final class StructuralProjection {

  private static final String ELIDED = "[ELIDED]";

  private StructuralProjection() {}

  /**
   * Returns the value-free structural form of the given canonical entry.
   *
   * @throws IllegalArgumentException if {@code entry} is null
   */
  public static CanonicalEntry project(CanonicalEntry entry) {
    if (entry == null) {
      throw new IllegalArgumentException("entry must not be null");
    }
    var projected = projectFields(entry);
    assert projected.ntReturnValue() == null : "postcondition: return value elided";
    assert projected.exceptionMessage() == null : "postcondition: exception message elided";
    return projected;
  }

  private static CanonicalEntry projectFields(CanonicalEntry entry) {
    return entry.toBuilder()
        .message(structuralMessage(entry))
        .ntParameters(elideParameters(entry.ntParameters()))
        .ntReturnValue(null)
        .exceptionMessage(null)
        .build();
  }

  private static String structuralMessage(CanonicalEntry entry) {
    if ("method_enter".equals(entry.ntEventType())) {
      var names =
          entry.ntParameters() == null
              ? ""
              : entry.ntParameters().stream()
                  .map(ParameterEntry::name)
                  .collect(java.util.stream.Collectors.joining(", "));
      return "→ " + entry.codeNamespace() + '.' + entry.codeFunction() + '(' + names + ')';
    }
    if ("method_exit".equals(entry.ntEventType())) {
      return structuralExitMessage(entry);
    }
    return entry.message();
  }

  private static String structuralExitMessage(CanonicalEntry entry) {
    if (entry.exceptionType() != null) {
      return "!! " + entry.exceptionType();
    }
    var name = entry.codeNamespace() + '.' + entry.codeFunction();
    if ("incomplete".equals(entry.ntOutcome())) {
      return "← " + name + " incomplete";
    }
    return "← " + name + " returned";
  }

  private static List<ParameterEntry> elideParameters(List<ParameterEntry> parameters) {
    if (parameters == null) return null;
    return parameters.stream()
        .map(p -> new ParameterEntry(p.name(), ELIDED, p.redacted(), p.type()))
        .toList();
  }
}
