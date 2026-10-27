/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

/**
 * The one warning the plugin logs when {@code agent { depth }} or {@code maxInternalSpans} is set
 * while {@code mode} is not {@code "agent"}.
 *
 * <p><b>@llmNote</b> The sentence is the same one the installation guide and the plugin guide open
 * their deep-traces entries with, and the one the doctor's {@code config.agent-depth-mode} finding
 * carries; the mode named in it is the one the build actually configured.
 */
final class DeepTracesModeWarning {

  private DeepTracesModeWarning() {}

  static String message(String mode) {
    return "narrativeTrace: agent { depth / maxInternalSpans } is set, but mode is \""
        + mode
        + "\". Deep traces require the Java agent (mode = \"agent\" in the plugin, or the"
        + " -javaagent option): the proxy route cannot see private methods or self-calls, so with"
        + " mode = \""
        + mode
        + "\" the setting has no effect.";
  }
}
