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
 * package filtering, then asks whether weaving this class would actually link, before delegating to
 * the heavier ASM transformer.
 *
 * <p><b>@llmNote</b> The gates are ordered, and the order is load-bearing. The package filter runs
 * first so a class nobody asked to instrument costs one {@code startsWith} and says nothing about
 * its loader; {@link ExcludedPackages} runs next — list 2, configurable, {@code exclude=} always
 * winning over {@code packages=}; {@link GeneratedClassNames} runs after that — list 1's name-shape
 * rule, cheap and silent, so a generator-shaped class never triggers a loader-visibility report
 * either; {@link LoaderVisibility} runs last so a matched, non-excluded, non-generated class whose
 * loader cannot resolve {@link AgentRuntime} loads uninstrumented instead of becoming a {@link
 * NoClassDefFoundError} in the host's own call (B-125).
 */
public final class NarrativeClassFileTransformer implements ClassFileTransformer {

  private final AgentConfig config;
  private final ExcludedPackages excludedPackages;
  private final LoaderVisibility loaderVisibility = new LoaderVisibility();

  public NarrativeClassFileTransformer(AgentConfig config) {
    this.config = config;
    this.excludedPackages = new ExcludedPackages(config.exclude());
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
    if (excludedPackages.isExcluded(className)) {
      return null; // list 2, configurable — ExcludedPackages reported the matched prefix, once
    }
    if (GeneratedClassNames.isGenerated(className)) {
      return null; // list 1, structural, silent — see GeneratedClassNames
    }
    if (!loaderVisibility.canReachRuntime(loader, className)) {
      return null; // woven bytecode would not link from here — LoaderVisibility said so, once
    }

    try {
      return ClassTransformer.transform(classfileBuffer, className, loader, config.depth());
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
