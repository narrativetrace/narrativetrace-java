/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.spring;

import io.micrometer.context.ContextSnapshotFactory;
import java.util.Map;
import org.springframework.core.task.TaskDecorator;

/**
 * Spring {@link TaskDecorator} that carries the NarrativeTrace context — and, when SLF4J is on the
 * classpath, the MDC — from the submitting thread onto the executing thread.
 *
 * <p>INTENT: Wire this into any {@code ThreadPoolTaskExecutor} whose tasks should appear in the
 * same trace as the code that submitted them. Without it, an {@code @Async} call runs with an empty
 * context and its narration is lost entirely — the calls simply do not appear in the trace tree.
 *
 * <pre>{@code
 * @Bean
 * ThreadPoolTaskExecutor taskExecutor() {
 *   var executor = new ThreadPoolTaskExecutor();
 *   executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
 *   executor.initialize();
 *   return executor;
 * }
 * }</pre>
 *
 * <p>Two things travel, and they are captured at <em>decoration</em> time — when the task is
 * submitted, not when it runs:
 *
 * <ul>
 *   <li>a Micrometer {@link io.micrometer.context.ContextSnapshot}, which carries the
 *       NarrativeTrace context through the registered {@code NarrativeTraceThreadLocalAccessor}.
 *       This is the part tracing needs.
 *   <li>the SLF4J MDC, for log correlation. Optional: use {@link #withoutMdc()} when the
 *       application does not use MDC, or when another decorator already owns it.
 * </ul>
 *
 * <p>Requires {@code io.micrometer:context-propagation} on the runtime classpath, and an accessor
 * registered with {@code ContextRegistry} (see the Spring integration guide). SLF4J is optional:
 * MDC propagation switches itself off when {@code org.slf4j.MDC} is absent.
 *
 * <p><b>@sideEffects</b> Mutates the executing thread's MDC for the duration of the task and
 * restores the thread's previous map afterwards, including when the task throws.
 *
 * <p><b>@llmNote</b> This is deliberately a plain class, not an auto-registered bean: an executor's
 * decorator is a single slot, so wiring it is the application's decision. Compose it — or copy it —
 * when an application already has a decorator of its own.
 *
 * <p><b>@pattern</b> Decorator — wraps the submitted {@link Runnable}, adding context transfer
 * around it.
 */
public class ContextPropagatingTaskDecorator implements TaskDecorator {

  private static final boolean MDC_PRESENT =
      isMdcOnClasspath(ContextPropagatingTaskDecorator.class.getClassLoader());

  private final boolean propagateMdc;

  /** Propagates the narrative context and, when SLF4J is present, the MDC. */
  public ContextPropagatingTaskDecorator() {
    this(true);
  }

  private ContextPropagatingTaskDecorator(boolean propagateMdc) {
    this.propagateMdc = propagateMdc;
    assert invariant() : "cached SLF4J availability disagrees with the classpath";
  }

  /**
   * Propagates the narrative context only, leaving the executing thread's MDC untouched. Use it
   * when the application does not use MDC, or when another decorator in the chain owns it.
   *
   * @return a decorator that copies the snapshot and nothing else
   */
  public static ContextPropagatingTaskDecorator withoutMdc() {
    return new ContextPropagatingTaskDecorator(false);
  }

  @Override
  public Runnable decorate(Runnable runnable) {
    if (runnable == null) {
      throw new IllegalArgumentException("runnable must not be null");
    }
    var snapshot = ContextSnapshotFactory.builder().build().captureAll();
    var wrapped = snapshot.wrap(runnable);
    var decorated = propagateMdc && MDC_PRESENT ? MdcTransfer.around(wrapped) : wrapped;
    assert decorated != null : "decorate must return a runnable";
    return decorated;
  }

  /**
   * True when the decorator is in a consistent state: the SLF4J availability decided once at class
   * load still matches the classpath, so a decorator asking for MDC transfer can perform it.
   */
  final boolean invariant() {
    return MDC_PRESENT == isMdcOnClasspath(ContextPropagatingTaskDecorator.class.getClassLoader());
  }

  /**
   * Package-private for the absent-SLF4J test, which passes a class loader that hides it.
   *
   * <p><b>@edgeCase</b> Catches {@link Throwable}, not just {@link ClassNotFoundException}. Even
   * with {@code initialize = false}, resolving a class can raise a {@link LinkageError} — a
   * truncated jar, a class compiled for a newer bytecode version, a duplicate on the classpath.
   * This runs from a static initialiser, so an escaping error does not merely answer "no SLF4J": it
   * poisons this decorator class for the life of the JVM, and every task submission through it
   * fails with {@code NoClassDefFoundError}. "SLF4J is not usably present" is the honest answer to
   * every one of those outcomes.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // classpath probing: any failure means "absent"
  static boolean isMdcOnClasspath(ClassLoader loader) {
    try {
      Class.forName("org.slf4j.MDC", false, loader);
      return true;
    } catch (Throwable absentOrUnusable) { // NOPMD
      return false;
    }
  }

  /**
   * Holds every direct reference to SLF4J. Loaded only when MDC propagation is actually used, so an
   * application without SLF4J never triggers resolution of {@code org.slf4j.MDC}.
   */
  private static final class MdcTransfer {

    private MdcTransfer() {}

    static Runnable around(Runnable task) {
      Map<String, String> callerMdc = org.slf4j.MDC.getCopyOfContextMap();
      return () -> {
        Map<String, String> executorMdc = org.slf4j.MDC.getCopyOfContextMap();
        apply(callerMdc);
        try {
          task.run();
        } finally {
          apply(executorMdc);
        }
      };
    }

    private static void apply(Map<String, String> mdc) {
      if (mdc == null) {
        org.slf4j.MDC.clear();
      } else {
        org.slf4j.MDC.setContextMap(mdc);
      }
    }
  }
}
