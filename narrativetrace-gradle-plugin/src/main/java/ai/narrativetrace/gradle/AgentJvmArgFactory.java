/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import java.util.List;

/**
 * Builds the {@code -javaagent} JVM argument for agent-mode test tasks.
 *
 * <p>INTENT: The emitted options string is a contract with {@code AgentConfig.parse} in the agent
 * module, which requires {@code key=value} entries — package filters must be passed as {@code
 * packages=pkg1;pkg2}, never as a bare list.
 */
final class AgentJvmArgFactory {

  private AgentJvmArgFactory() {}

  static String javaagentArg(String agentJarPath, List<String> packages) {
    var packagesSuffix = packages.isEmpty() ? "" : "=packages=" + String.join(";", packages);
    return "-javaagent:" + agentJarPath + packagesSuffix;
  }
}
