/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

/**
 * One template string from {@code templates.json}.
 *
 * @param id stable kebab-case identifier
 * @param description what the case is testing
 * @param template the materialized template
 * @param values names the fixture graph it resolves against ({@code card}, {@code user}, {@code
 *     order}, {@code deep}, {@code unicode}, {@code wide}, {@code chain}); see {@link
 *     HostileGraphs#templateValues(String, String)}
 * @param expect {@code "redacted"} when the result must carry the redaction marker and not the
 *     secret, {@code null} when only the universal oracles apply
 */
public record TemplateCase(
    String id, String description, String template, String values, String expect) {

  /** Whether this case pins the redaction invariant rather than only the universal oracles. */
  public boolean expectsRedaction() {
    return "redacted".equals(expect);
  }

  @Override
  public String toString() {
    return id;
  }
}
