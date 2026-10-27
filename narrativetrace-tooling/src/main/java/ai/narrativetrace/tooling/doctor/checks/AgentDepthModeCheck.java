/*
 * Copyright (c) 2026 Empower Agile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.narrativetrace.tooling.doctor.checks;

import ai.narrativetrace.tooling.doctor.DocAnchors;
import ai.narrativetrace.tooling.doctor.DoctorCheck;
import ai.narrativetrace.tooling.doctor.DoctorSnapshot;
import ai.narrativetrace.tooling.doctor.Finding;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * {@code config.agent-depth-mode} — {@code agent { depth … }} or {@code maxInternalSpans} in a
 * build file while the plugin's {@code mode} is not {@code "agent"}.
 *
 * <p>INTENT: deep traces need the Java agent. The proxy route intercepts calls through the wrapped
 * reference, so private methods and {@code this} self-calls never pass it; and {@code proxy} is the
 * plugin's default mode. An adopter who sets {@code depth} and leaves {@code mode} alone gets no
 * deep spans and no explanation — this finding is the explanation.
 *
 * <p><b>@llmNote</b> A WARN, never a FAIL: the build works and the tests pass, the setting just
 * does nothing. Read from live build-script code (comments removed) with whitespace collapsed;
 * {@code mode} counts as agent when ANY Gradle script in the project sets it ({@code
 * mode.set("agent")}, {@code mode = "agent"}), so a module that inherits the mode from the root is
 * not warned about. Only the {@code depth} and {@code maxInternalSpans} properties INSIDE an {@code
 * agent { … }} block count; a local named {@code depth} elsewhere is not the setting.
 */
public final class AgentDepthModeCheck implements DoctorCheck {

  public static final String ID = "config.agent-depth-mode";

  static final String FIX_LINE = "mode.set(\"agent\")";

  private static final Pattern MODE_AGENT =
      Pattern.compile("(?<![\\w$.])mode(?:\\.set\\(|=)[\"']agent[\"']");

  private static final Pattern DEPTH_SETTING =
      Pattern.compile("(?<![\\w$.])(?:depth|maxInternalSpans)(?:\\.set\\(|\\.convention\\(|=)");

  @Override
  public Finding run(DoctorSnapshot snapshot) {
    Map<String, String> scripts = snapshot.buildScripts();
    boolean agentMode =
        scripts.values().stream().anyMatch(code -> MODE_AGENT.matcher(compact(code)).find());
    if (agentMode) {
      return Finding.pass(
          ID,
          "mode is \"agent\" — agent { depth } and maxInternalSpans reach the -javaagent argument",
          DocAnchors.DEEP_TRACES);
    }
    for (Map.Entry<String, String> script : scripts.entrySet()) {
      if (setsDepthInAgentBlock(compact(script.getValue()))) {
        return Finding.warn(
            ID,
            script.getKey()
                + " sets agent { depth … } or maxInternalSpans while mode is not \"agent\" (absent"
                + " means proxy): deep traces require the Java agent, the proxy route cannot see"
                + " private methods or self-calls, so the setting has no effect under the proxy"
                + " route",
            "Use the agent route: narrativeTrace { "
                + FIX_LINE
                + " } — or drop the agent { depth … } setting. The -javaagent option gives the"
                + " same route without the plugin.",
            DocAnchors.DEEP_TRACES);
      }
    }
    return Finding.pass(
        ID,
        "no agent { depth … } or maxInternalSpans set — nothing to take effect",
        DocAnchors.DEEP_TRACES);
  }

  private static String compact(String code) {
    return code.replaceAll("\\s+", "");
  }

  /** True when a {@code depth}/{@code maxInternalSpans} setting sits inside an {@code agent{…}}. */
  private static boolean setsDepthInAgentBlock(String compact) {
    int from = 0;
    while (true) {
      int at = compact.indexOf("agent{", from);
      if (at < 0) {
        return false;
      }
      boolean wordStart = at == 0 || !Character.isLetterOrDigit(compact.charAt(at - 1));
      int open = at + "agent".length();
      int close = matchingBrace(compact, open);
      if (wordStart && DEPTH_SETTING.matcher(compact.substring(open + 1, close)).find()) {
        return true;
      }
      from = open + 1;
    }
  }

  /** The index of the {@code }} closing the {@code {} at {@code open}, or the text's end. */
  private static int matchingBrace(String s, int open) {
    int level = 0;
    for (int i = open; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '{') {
        level++;
      } else if (c == '}') {
        level--;
        if (level == 0) {
          return i;
        }
      }
    }
    return s.length();
  }
}
