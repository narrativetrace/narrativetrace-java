/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

/**
 * Java agent entry point invoked by the JVM's {@code premain} hook.
 *
 * <p>INTENT: Parse configuration, make any user-supplied logging jars visible, initialize shared
 * runtime state, and register the class file transformer before application code loads.
 *
 * <p>{@code loggingJars} entries are appended to the system classloader search <em>before</em>
 * {@link AgentRuntime#initialize} runs, because initialization reflectively looks up the SLF4J
 * listener — the user's provider must already be resolvable at that point. This is the supported
 * way to hand the standalone jar an SLF4J binding on hosts with no reachable classpath (app
 * servers); plain-java hosts can simply put the provider on {@code -cp} instead.
 */
public final class NarrativeTraceAgent {

  private NarrativeTraceAgent() {}

  public static void premain(String agentArgs, Instrumentation inst) {
    var config = AgentConfig.parse(agentArgs);
    if (inst != null) {
      appendLoggingJars(config, inst);
    }
    AgentRuntime.initialize(config);
    if (inst != null) {
      inst.addTransformer(new NarrativeClassFileTransformer(config));
    }
  }

  @SuppressWarnings("PMD.CloseResource") // the classloader owns the JarFile for the JVM's lifetime
  private static void appendLoggingJars(AgentConfig config, Instrumentation inst) {
    for (var jar : config.loggingJars()) {
      try {
        inst.appendToSystemClassLoaderSearch(new JarFile(jar.toFile()));
      } catch (IOException e) {
        throw new UncheckedIOException("loggingJars entry is not a readable jar: " + jar, e);
      }
    }
  }
}
