/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import java.util.List;

/**
 * A DDD bounded context declared in the glossary, mapped to package prefixes.
 *
 * <p>INTENT: Contexts scope term identity — the same normalized term may exist independently in two
 * contexts with different definitions and translations. Package prefixes are matched
 * delimiter-aware by {@code ContextResolver}; an empty prefix list is valid (the {@code
 * _unassigned} fallback context declares none).
 *
 * @param name context name, unique within a glossary; never blank
 * @param packages package prefixes owned by this context; may be empty, never {@code null}
 * @param description human-written summary of the context's domain (may be {@code null})
 */
public record BoundedContext(String name, List<String> packages, String description) {

  public BoundedContext {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("context name must not be blank");
    }
    if (packages == null) {
      throw new IllegalArgumentException("packages must not be null");
    }
    packages = List.copyOf(packages);
  }
}
