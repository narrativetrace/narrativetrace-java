/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the {@code -javaagent} JVM argument for agent-mode test tasks.
 *
 * <p>INTENT: The emitted options string is a contract with {@code AgentConfig.parse} in the agent
 * module, which requires {@code key=value} entries — package filters must be passed as {@code
 * packages=pkg1;pkg2}, never as a bare list. {@code exclude} follows the same contract: list 2, the
 * agent's configurable, published-default exclusion (see the installation guide's "Exclude
 * packages" section).
 */
final class AgentJvmArgFactory {

  private AgentJvmArgFactory() {}

  static String javaagentArg(String agentJarPath, List<String> packages, List<String> exclude) {
    return javaagentArg(agentJarPath, packages, exclude, null, null);
  }

  /**
   * As above, plus the opt-in deep mode: {@code depth=internal} and {@code maxInternalSpans=<n>}
   * are appended only when set, so a build that sets neither emits the argument it always did.
   *
   * @param depth the agent's {@code depth} option, or {@code null}/blank for none
   * @param maxInternalSpans the agent's per-scope cap on deep spans, or {@code null} for none
   */
  static String javaagentArg(
      String agentJarPath,
      List<String> packages,
      List<String> exclude,
      String depth,
      Integer maxInternalSpans) {
    var segments = new ArrayList<String>();
    if (!packages.isEmpty()) {
      segments.add("packages=" + String.join(";", packages));
    }
    if (!exclude.isEmpty()) {
      segments.add("exclude=" + String.join(";", exclude));
    }
    if (depth != null && !depth.isBlank()) {
      segments.add("depth=" + depth.trim());
    }
    if (maxInternalSpans != null) {
      segments.add("maxInternalSpans=" + maxInternalSpans);
    }
    var suffix = segments.isEmpty() ? "" : "=" + String.join(",", segments);
    return "-javaagent:" + agentJarPath + suffix;
  }
}
