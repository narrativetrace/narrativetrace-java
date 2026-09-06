/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * {@link ClassFileTransformer} that instruments only configured packages.
 *
 * <p>INTENT: This is the JVM-facing entry point used by {@code Instrumentation}. It performs fast
 * package filtering before delegating to the heavier ASM transformer.
 */
public final class NarrativeClassFileTransformer implements ClassFileTransformer {

  private final AgentConfig config;

  public NarrativeClassFileTransformer(AgentConfig config) {
    this.config = config;
  }

  // ASM failures surface as Errors too (LinkageError, VerifyError); the JVM discards anything
  // thrown from a ClassFileTransformer, so a narrower catch would only lose the warning.
  @SuppressWarnings("PMD.AvoidCatchingThrowable")
  @Override
  public byte[] transform(
      ClassLoader loader,
      String className,
      Class<?> classBeingRedefined,
      ProtectionDomain protectionDomain,
      byte[] classfileBuffer) {
    if (className == null || !config.shouldTransform(className)) {
      return null; // no transformation
    }

    try {
      return ClassTransformer.transform(classfileBuffer, className, loader);
    } catch (Throwable failure) {
      // The JVM silently discards exceptions thrown by a ClassFileTransformer; without this
      // line the class loads uninstrumented and its narration is just absent, with no clue why.
      System.err.println(
          "narrativetrace-agent: cannot instrument "
              + className
              + " ("
              + failure
              + "); class loads uninstrumented");
      return null;
    }
  }
}
