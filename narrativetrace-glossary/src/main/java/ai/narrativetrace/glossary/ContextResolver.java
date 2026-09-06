/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

/**
 * Resolves a Java package name to the bounded context that owns it.
 *
 * <p>INTENT: Contexts declare package prefixes in the glossary file (single source of truth).
 * Matching is delimiter-aware — {@code com.acme.billing} owns {@code com.acme.billing.overdraft}
 * but not {@code com.acme.billingx}. The longest matching prefix wins, so nested contexts are
 * possible. No match falls back to {@link #UNASSIGNED}, letting harvesting work with zero
 * configuration.
 */
public final class ContextResolver {

  /** Fallback context for packages no declared context owns. */
  public static final String UNASSIGNED = "_unassigned";

  private final Glossary glossary;

  /**
   * @param glossary glossary whose declared contexts drive resolution; must not be {@code null}
   */
  public ContextResolver(Glossary glossary) {
    if (glossary == null) {
      throw new IllegalArgumentException("glossary must not be null");
    }
    this.glossary = glossary;
  }

  /**
   * Resolves a package name to a declared context name.
   *
   * @param packageName fully qualified package of the declaring class; must not be {@code null}
   * @return the owning context's name, or {@link #UNASSIGNED} when no declared prefix matches
   */
  public String resolve(String packageName) {
    if (packageName == null) {
      throw new IllegalArgumentException("packageName must not be null");
    }
    var best = UNASSIGNED;
    var bestLength = -1;
    // Sorted iteration makes equal-length prefix ties deterministic (first name wins).
    for (var name : glossary.contexts().keySet().stream().sorted().toList()) {
      for (var prefix : glossary.contexts().get(name).packages()) {
        if (owns(prefix, packageName) && prefix.length() > bestLength) {
          best = name;
          bestLength = prefix.length();
        }
      }
    }
    return best;
  }

  /** Delimiter-aware prefix test: equal, or a child package separated by a dot. */
  private static boolean owns(String prefix, String packageName) {
    return packageName.equals(prefix) || packageName.startsWith(prefix + ".");
  }
}
