/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Whether a class loader can resolve the runtime that instrumented bytecode links to.
 *
 * <p>INTENT: Every hook {@link NarrativeMethodVisitor} emits is an {@code INVOKESTATIC
 * ai/narrativetrace/agent/AgentRuntime}, baked into the woven class. A class whose defining loader
 * cannot resolve that type therefore links to nothing, and the failure lands as a {@link
 * NoClassDefFoundError} inside the host's own call — the agent turning observability into an
 * application failure, which this library does not do. So the precondition is asked before the
 * class is woven, not discovered afterwards.
 *
 * <p>The rule is stated over visibility and never over {@code loader == null}: the bootstrap loader
 * is the case that reached a published artifact (mockito's inline mock maker injects {@code
 * MockMethodDispatcher} there, and {@code packages=org.mockito.*} matched it — B-125), but an
 * isolated loader with no parent delegation, an OSGi bundle that does not import the agent's
 * package, and a sealed deployment loader are the same defect with a non-null loader.
 *
 * <p><b>@llmNote</b> Identity, not mere resolution (B-125 follow-up). A loader that defines its
 * <em>own</em> copy of {@code AgentRuntime} is refused too, exactly like OpenTelemetry's {@code
 * IgnoredClassLoadersMatcher} and Datadog's {@code ClassLoaderMatchers.incompatibleClassLoader}:
 * that copy would link, but into a runtime nothing initialised, so weaving against it would trace
 * nothing and narrate nothing — a quiet degradation this class also exists to prevent, not only the
 * host-crashing one.
 *
 * <p><b>@threadSafety</b> Called from the class-loading path of every thread, concurrently. The
 * verdict cache has weak keys so a deployment that discards a loader does not pin it, and the probe
 * deliberately runs <em>outside</em> the cache's monitor: {@link Class#forName} takes the loader's
 * own lock, and holding a shared monitor across that is how an agent deadlocks a host whose loader
 * is not parallel-capable. The cost of that choice is a duplicate probe under a race; {@code
 * putIfAbsent} still elects exactly one reporter per loader, so the diagnostic cannot double.
 *
 * <p><b>@sideEffects</b> Prints one line to {@code stderr} per loader that cannot see the runtime —
 * never per class. A hot loader with thousands of matching classes must not flood a host's error
 * stream to say one thing once.
 */
final class LoaderVisibility {

  /** The class every injected hook calls; resolving it is exactly what weaving requires. */
  static final String RUNTIME_CLASS = "ai.narrativetrace.agent.AgentRuntime";

  /**
   * Verdict per loader, weakly keyed. A {@code null} key is the bootstrap loader — {@link
   * WeakHashMap} keeps it as a sentinel, so "already reported" is one mechanism for every loader
   * rather than a boolean beside the map.
   */
  private final Map<ClassLoader, Boolean> verdicts =
      Collections.synchronizedMap(new WeakHashMap<>());

  /**
   * Whether a class this loader defines may be instrumented, reporting the refusal once per loader.
   *
   * @param loader the loader defining the class, {@code null} for the bootstrap loader
   * @param className the internal name of the class that prompted the question, for the diagnostic
   * @return {@code true} when weaving this loader's classes links
   */
  boolean canReachRuntime(ClassLoader loader, String className) {
    var known = verdicts.get(loader);
    if (known != null) {
      return known;
    }
    var reachable = loader != null && resolvesRuntime(loader);
    var firstVerdict = verdicts.putIfAbsent(loader, reachable) == null;
    if (!reachable && firstVerdict) {
      reportUnreachable(loader, className);
    }
    return reachable;
  }

  /**
   * Whether {@link #RUNTIME_CLASS} resolves through this loader to the agent's own class, without
   * initialising it.
   *
   * <p><b>@edgeCase</b> Identity, not mere resolution: a loader holding a second copy of {@code
   * AgentRuntime} — its own classpath carries a stray copy of the agent jar, or an isolated loader
   * was handed the class file directly — resolves the name but would link the woven class to a
   * runtime nothing initialised, so it would trace and narrate nothing. Matches the rule
   * OpenTelemetry's {@code IgnoredClassLoadersMatcher} and Datadog's {@code
   * ClassLoaderMatchers.incompatibleClassLoader} both apply.
   *
   * <p><b>@edgeCase</b> Catches {@link Throwable}, not just {@link ClassNotFoundException}. This
   * asks an arbitrary host loader — supplied by an application server, a plugin framework or a
   * build tool — to resolve a name, and a loader that is broken, closed, or refuses the package
   * answers with {@link LinkageError} or {@link SecurityException} rather than a checked exception.
   * Every one of them means the same thing here: weaving would not link, so do not weave.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // an arbitrary host loader may fail any way
  private static boolean resolvesRuntime(ClassLoader loader) {
    try {
      return loader.loadClass(RUNTIME_CLASS) == AgentRuntime.class;
    } catch (Throwable unreachable) { // NOPMD - every failure mode means "would not link"
      return false;
    }
  }

  private static void reportUnreachable(ClassLoader loader, String className) {
    System.err.println( // NOPMD - the agent's only channel from inside a class transformer
        "narrativetrace-agent: cannot instrument "
            + className
            + " ("
            + RUNTIME_CLASS
            + " is not visible from "
            + describe(loader)
            + "); classes from that loader load uninstrumented");
  }

  /**
   * The loader, named by its own type and identity rather than by its {@code toString()}.
   *
   * <p>INTENT: A host's loader may override {@code toString()} with anything — a megabyte of
   * classpath, or a method that throws. This line is printed from inside a class transformer while
   * the host loads a class; it reads state and runs no host code, for the same reason value
   * rendering does not.
   */
  private static String describe(ClassLoader loader) {
    if (loader == null) {
      return "the bootstrap class loader";
    }
    return "class loader "
        + loader.getClass().getName()
        + "@"
        + Integer.toHexString(System.identityHashCode(loader));
  }
}
