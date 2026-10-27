/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.EventPipeline;
import ai.narrativetrace.core.pipeline.PipelineBootstrap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The one context, config and event pipeline every TestNG test in a JVM shares — the lifecycle
 * owner {@code narrativetrace-junit4}'s {@code SuiteContext} and {@code narrativetrace-junit5}'s
 * {@code SuiteContextResource} are for their frameworks.
 *
 * <p>INTENT: A pipeline owns an eagerly allocated event ring and an event store. One per test
 * method — or per class — is what exhausted a JUnit 4 test heap at a few hundred classes (B-150),
 * so the test JVM, not the test, owns the pipeline: the first test builds it, every later one
 * reuses it, and each test is a request-scoped slice of it — {@link
 * ThreadLocalNarrativeContext#reset()} at the test's start and again after its capture removes that
 * test's spans and stored events.
 *
 * <p><b>@llmNote</b> Closed by one JVM shutdown hook, never by the end of a TestNG execution: one
 * JVM can run several executions (an IDE, a harness, a build that forks once), and an execution end
 * that closed the pipeline would leave the next one publishing into a closed pipeline. {@link
 * EventPipeline#close()} is idempotent and publishing into a closed pipeline is defined behaviour,
 * so the hook firing early costs events, never correctness.
 *
 * <p><b>@sideEffects</b> Registers exactly one JVM shutdown hook, on first use. The ring is sized
 * by {@code narrativetrace.bufferCapacity} (default {@value #DEFAULT_TEST_BUFFER_CAPACITY} slots),
 * read once, here; the runtime-wide {@code narrativetrace.buffer.capacity} still overrules it.
 */
final class SharedPipeline {

  /**
   * Slots in the shared event ring, when nothing configures it — the JUnit 5 extension's test-sized
   * default, for its reason: a test traces tens of calls, and the runtime's 65,536-slot default is
   * allocated eagerly for slots no test reaches.
   */
  static final int DEFAULT_TEST_BUFFER_CAPACITY = 8192;

  /** System property overriding {@link #DEFAULT_TEST_BUFFER_CAPACITY}. */
  static final String BUFFER_CAPACITY_KEY = "narrativetrace.bufferCapacity";

  /**
   * How many shared instances {@link #shared()} has built in this JVM — the shared-pipeline proof
   * reads it, and it stays 1 however many tests and executions run.
   */
  private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

  /** Guarded by this class's monitor; built on first use, never replaced. */
  private static SharedPipeline shared;

  private final NarrativeTraceConfig config;
  private final EventPipeline pipeline;
  private final ThreadLocalNarrativeContext context;
  private volatile AgentContextBridge agentBridge;

  /** Package-private rather than private: a test needs an instance it may close on its own. */
  SharedPipeline(int bufferCapacity) {
    this.config = new NarrativeTraceConfig(TracingLevel.DETAIL);
    this.pipeline =
        PipelineBootstrap.createDefault(PipelineBootstrap.DEFAULT_LOGGER_NAME, bufferCapacity);
    this.context = new ThreadLocalNarrativeContext(config, pipeline);
    this.agentBridge = AgentContextBridge.attach(context);
  }

  /**
   * This JVM's shared pipeline, building it — and its one shutdown hook — on first use.
   *
   * @return The instance every test in this JVM uses. Never {@code null}.
   */
  @SuppressWarnings("PMD.DoNotUseThreads") // a shutdown hook IS a Thread; there is no other form
  static synchronized SharedPipeline shared() {
    if (shared == null) {
      var built = new SharedPipeline(bufferCapacityFrom(System.getProperty(BUFFER_CAPACITY_KEY)));
      CONSTRUCTIONS.incrementAndGet();
      Runtime.getRuntime()
          .addShutdownHook(new Thread(built::close, "narrative-trace-testng-pipeline"));
      shared = built;
    }
    return shared;
  }

  /** How many shared instances — and so default pipelines — {@link #shared()} has built. */
  static int constructions() {
    return CONSTRUCTIONS.get();
  }

  /**
   * Reads a configured ring size, defaulting to {@link #DEFAULT_TEST_BUFFER_CAPACITY}.
   *
   * <p><b>@edgeCase</b> Absent, blank, non-numeric, zero and negative values all degrade to the
   * default rather than failing: a typo in an observability knob must not turn a green suite red.
   *
   * @param configured the raw property value, possibly {@code null} or blank
   */
  static int bufferCapacityFrom(String configured) {
    if (configured == null || configured.isBlank()) {
      return DEFAULT_TEST_BUFFER_CAPACITY;
    }
    try {
      var requested = Integer.parseInt(configured.trim());
      return requested >= 1 ? requested : DEFAULT_TEST_BUFFER_CAPACITY;
    } catch (NumberFormatException e) {
      return DEFAULT_TEST_BUFFER_CAPACITY;
    }
  }

  /** The context every test resets, captures and resets again. */
  ThreadLocalNarrativeContext context() {
    return context;
  }

  /** The mutable config behind {@link #context()} — the level is set as each test starts. */
  NarrativeTraceConfig config() {
    return config;
  }

  /** The pipeline behind {@link #context()} — its store is what each test's reset empties. */
  EventPipeline pipeline() {
    return pipeline;
  }

  /** The bridge to the Java agent's runtime, when one is attached to this JVM. */
  AgentContextBridge agentBridge() {
    return agentBridge;
  }

  /**
   * Test seam: swaps the bridge, so a test can stand a fake agent runtime in for the real one.
   *
   * @return the bridge that was replaced
   */
  AgentContextBridge useAgentBridge(AgentContextBridge replacement) {
    var previous = agentBridge;
    agentBridge = replacement;
    return previous;
  }

  /** Restores the agent's previous context and closes the pipeline. Idempotent. */
  void close() {
    agentBridge.detach();
    pipeline.close();
  }
}
