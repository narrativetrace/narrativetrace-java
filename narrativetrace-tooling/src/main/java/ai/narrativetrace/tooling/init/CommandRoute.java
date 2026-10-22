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
package ai.narrativetrace.tooling.init;

/**
 * Which binary is writing the {@code AGENTS.md} managed section, and the three commands it names
 * for whoever reads it next.
 *
 * <p>INTENT: {@link AgentsMdBlock} renders a template; it must never be the one guessing which
 * spelling is true from what it can see on disk — a Gradle build file proves a project IS built
 * with Gradle, never that THIS install ran through the plugin rather than the launcher. Each route
 * is the one place that knows which binary it is, so each route carries its own three commands
 * here, once, and the renderer only ever asks for them.
 */
public enum CommandRoute {
  /**
   * The Gradle plugin's own tasks. The preview is spelled {@code --diff}: Gradle owns {@code
   * --dry-run} as a built-in that skips every task in the graph, so a section naming it would send
   * a reader to a command that prints nothing.
   */
  GRADLE_PLUGIN(
      "./gradlew narrativetraceDoctor",
      "./gradlew narrativetraceInit --diff",
      "./gradlew narrativetraceUninstall"),

  /** The {@code narrativetrace} launcher's own verbs, exactly as its own {@code --help} shows. */
  CLI("narrativetrace doctor", "narrativetrace init --dry-run", "narrativetrace uninstall");

  private final String doctorCommand;
  private final String diffCommand;
  private final String uninstallCommand;

  CommandRoute(String doctorCommand, String diffCommand, String uninstallCommand) {
    this.doctorCommand = doctorCommand;
    this.diffCommand = diffCommand;
    this.uninstallCommand = uninstallCommand;
  }

  /** The read-only diagnosis command. */
  public String doctorCommand() {
    return doctorCommand;
  }

  /** The command that previews a re-install as a diff, writing nothing. */
  public String diffCommand() {
    return diffCommand;
  }

  /** The command that removes exactly what the installer wrote. */
  public String uninstallCommand() {
    return uninstallCommand;
  }
}
