/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.EventPipeline;
import ai.narrativetrace.core.pipeline.PipelineBootstrap;

/**
 * The one context, config and event pipeline every {@link NarrativeTraceClassRule} in a test JVM
 * shares — JUnit 4's lifecycle owner for the suite, as {@code
 * NarrativeTraceExtension.SuiteContextResource} is JUnit 5's.
 *
 * <p>INTENT: A {@code @ClassRule} lives in a static field, and a test class never unloads, so
 * whatever the rule still holds when its class ends is retained for the life of the test JVM. One
 * pipeline per class therefore retained one eagerly allocated event ring per class — a few hundred
 * test classes exhausted the test heap on nothing but rings no test could reach any more. The
 * suite, not the class, is the lifecycle owner: the first class rule to start builds this once,
 * every later one reuses it, and the per-test boundary is unchanged — the linked {@link
 * NarrativeTraceRule} still resets before each test and captures after it.
 *
 * <p><b>@llmNote</b> Closed by a shutdown hook rather than by the class that happened to finish
 * last: JUnit 4 has no suite-end callback a library can hook without owning the runner, and a rule
 * cannot know whether another class is still to come. {@link EventPipeline#close()} is idempotent
 * and publishing into a closed pipeline is already defined behaviour, so an early close costs
 * events, never correctness.
 *
 * <p><b>@sideEffects</b> Registers exactly one JVM shutdown hook, on first use, in a JVM that runs
 * JUnit 4 class rules at all. The ring is sized by {@code narrativetrace.buffer.capacity} as
 * everywhere else; at the runtime default its ~1.75 MB is now paid once per test JVM instead of
 * once per test class.
 */
final class SuiteContext {

  /** Guarded by this class's monitor; built on first use, never replaced. */
  private static SuiteContext shared;

  private final NarrativeTraceConfig config;
  private final EventPipeline pipeline;
  private final ThreadLocalNarrativeContext context;

  /** Package-private rather than private: a test needs an instance it may close on its own. */
  SuiteContext() {
    this.config = new NarrativeTraceConfig(TracingLevel.DETAIL);
    this.pipeline = PipelineBootstrap.createDefault(PipelineBootstrap.DEFAULT_LOGGER_NAME);
    this.context = new ThreadLocalNarrativeContext(config, pipeline);
  }

  /**
   * This test JVM's suite context, building it — and its one shutdown hook — on first use.
   *
   * @return The shared context every class rule in this JVM uses. Never {@code null}.
   */
  @SuppressWarnings("PMD.DoNotUseThreads") // a shutdown hook IS a Thread; there is no other form
  static synchronized SuiteContext shared() {
    if (shared == null) {
      var suite = new SuiteContext();
      Runtime.getRuntime()
          .addShutdownHook(new Thread(suite::close, "narrative-trace-junit4-suite"));
      shared = suite;
    }
    return shared;
  }

  /** The context every class rule hands its linked per-test rules. */
  ThreadLocalNarrativeContext context() {
    return context;
  }

  /** The mutable config behind {@link #context()} — a linked rule sets the level per test. */
  NarrativeTraceConfig config() {
    return config;
  }

  /** Closes the suite's pipeline. Idempotent, and safe to call from the shutdown hook. */
  void close() {
    pipeline.close();
  }
}
