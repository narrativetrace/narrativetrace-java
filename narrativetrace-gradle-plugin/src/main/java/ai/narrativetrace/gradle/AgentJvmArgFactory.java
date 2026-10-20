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
    var segments = new ArrayList<String>();
    if (!packages.isEmpty()) {
      segments.add("packages=" + String.join(";", packages));
    }
    if (!exclude.isEmpty()) {
      segments.add("exclude=" + String.join(";", exclude));
    }
    var suffix = segments.isEmpty() ? "" : "=" + String.join(",", segments);
    return "-javaagent:" + agentJarPath + suffix;
  }
}
