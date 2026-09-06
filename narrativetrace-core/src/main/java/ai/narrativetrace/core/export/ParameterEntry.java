/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.export;

/**
 * Canonical representation of a method parameter in the entry schema.
 *
 * <p>INTENT: Maps to the {@code nt.parameters} array items in {@code entry.schema.json}. Each entry
 * has a name, serialized value, and optional redacted flag.
 *
 * @param name parameter name from the method signature
 * @param value serialized parameter value, or {@code "[REDACTED]"} when redacted
 * @param redacted {@code true} if the value was redacted by {@code @NotTraced} or equivalent
 * @param type declared parameter type in {@link Class#getTypeName()} form (e.g. {@code
 *     "java.lang.String"}, {@code "int"}, {@code "long[]"}), or {@code null} when the capture site
 *     could not supply it; added in schema 1.2 so overloads are distinguishable
 */
public record ParameterEntry(String name, String value, boolean redacted, String type) {

  /** Compatibility constructor for call sites that carry no declared type. */
  public ParameterEntry(String name, String value, boolean redacted) {
    this(name, value, redacted, null);
  }
}
