/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import ai.narrativetrace.api.event.TraceEvent;
import ai.narrativetrace.api.spi.TraceEventListener;
import ai.narrativetrace.core.config.ConfigResolver;
import ai.narrativetrace.core.spi.ExtensionRegistry;
import java.util.List;
import java.util.function.Consumer;

/**
 * The one place a pipeline is composed.
 *
 * <p>INTENT: Every integration — the plain context, Spring, the agent, the JUnit extension — asks
 * this class for its pipeline, so topology selection, optional narration, and extension placement
 * are decided once rather than re-derived (and drifting) per call site.
 *
 * <p>Three inputs, resolved in this order:
 *
 * <ol>
 *   <li><b>Durable narration.</b> When the slf4j module is on the classpath, its listener is
 *       composed onto the synchronous path automatically — narration is the assumed default rather
 *       than something each call site wires by hand. Set {@code narrativetrace.narration=off} to
 *       veto it.
 *   <li><b>Additive listeners.</b> Discovered {@link TraceEventListener}s are placed beside
 *       retention on the best-effort slot. Presence on the classpath is the activation signal.
 *   <li><b>Topology.</b> Absent {@code narrativetrace.pipeline}, the default dual-path topology is
 *       built directly — no factory lookup happens at all. Named, it must resolve to a discovered
 *       {@link EventPipelineFactory}. The default topology's fixed-size ring is sized by {@code
 *       narrativetrace.buffer.capacity}; a named topology reads its own namespaced settings
 *       instead.
 * </ol>
 *
 * <p><b>@llmNote</b> A named topology that resolves to nothing fails initialization instead of
 * falling back to the default. Durability is a correctness property: silently running a different
 * topology than the one configured is a worse outcome than not starting.
 *
 * <p><b>@sideEffects</b> Discovery runs once per bootstrap, at construction time of the pipeline —
 * never on the publish path. With nothing declared and no topology configured, the result is the
 * same {@link DualPathPipeline} the context built before extension points existed.
 */
public final class PipelineBootstrap {

  /** Names the topology to build; absent means the default dual-path topology. */
  static final String STRATEGY_KEY = "narrativetrace.pipeline";

  /** Set to {@code off} to suppress the automatically composed SLF4J narration listener. */
  static final String NARRATION_KEY = "narrativetrace.narration";

  /**
   * Slots in the default topology's fixed-size ring; absent means whatever default the caller
   * passed to {@link #build(String, int)}, which is {@link BufferedEventConsumer#DEFAULT_CAPACITY}
   * unless an integration chose otherwise.
   *
   * <p>Sizing rule: {@code peak events/s × worst tolerable drain stall}, with roughly 300 B of
   * retained memory per event at saturation.
   */
  static final String CAPACITY_KEY = "narrativetrace.buffer.capacity";

  /** Logger name used when a caller does not choose one. */
  public static final String DEFAULT_LOGGER_NAME = "narrativetrace";

  private static final String SLF4J_LISTENER_CLASS =
      "ai.narrativetrace.slf4j.Slf4jTraceEventListener";
  private static final String OFF = "off";

  private final ConfigResolver config;
  private final ExtensionRegistry registry;
  private final String narrationListenerClass;

  /** Creates a bootstrap resolving configuration and extensions from the ambient classpath. */
  public PipelineBootstrap() {
    this(new ConfigResolver(), new ExtensionRegistry());
  }

  /**
   * Creates a bootstrap with explicit collaborators.
   *
   * @param config resolver for topology and narration keys
   * @param registry registry used to discover listeners and pipeline factories
   */
  public PipelineBootstrap(ConfigResolver config, ExtensionRegistry registry) {
    this(config, registry, SLF4J_LISTENER_CLASS);
  }

  /**
   * Test seam: which class carries narration.
   *
   * <p>INTENT: The failure this exists for — a narration class that will not initialise — cannot be
   * staged against the real one, because the real one either is on the classpath and works or is
   * absent. Package-private, because which class narrates is a fact about the module layout, not a
   * deployment choice.
   *
   * @param config resolver for topology and narration keys
   * @param registry registry used to discover listeners and pipeline factories
   * @param narrationListenerClass fully qualified name of the durable narration listener
   */
  PipelineBootstrap(
      ConfigResolver config, ExtensionRegistry registry, String narrationListenerClass) {
    if (config == null || registry == null) {
      throw new IllegalArgumentException("config and registry must not be null");
    }
    this.config = config;
    this.registry = registry;
    this.narrationListenerClass = narrationListenerClass;
  }

  /**
   * Builds the configured pipeline with the default logger name.
   *
   * @return A ready pipeline. Never {@code null}.
   */
  public static EventPipeline createDefault() {
    return createDefault(DEFAULT_LOGGER_NAME);
  }

  /**
   * Builds the configured pipeline narrating under the given logger name.
   *
   * @param loggerName logger name for the durable narration listener; {@code null} or empty
   *     suppresses narration just as the configuration veto does
   * @return A ready pipeline. Never {@code null}.
   */
  public static EventPipeline createDefault(String loggerName) {
    return new PipelineBootstrap().build(loggerName);
  }

  /**
   * Builds the configured pipeline, sizing the default topology's ring from the integration's own
   * default rather than the library's.
   *
   * <p>INTENT: An integration that knows its workload should say so, explicitly, in its own code. A
   * test harness constructs one context per test method and uses a few dozen slots of it, so {@code
   * narrativetrace-junit5} passes 8,192 here. Core does not — and must not — detect JUnit or any
   * other framework: a runtime that behaves differently because of what is on the classpath is a
   * runtime nobody can reason about. The integration owns its default, visibly.
   *
   * <p><b>@edgeCase</b> This is a <em>default</em>, not an override: {@code
   * narrativetrace.buffer.capacity} — system property first, then {@code narrativetrace.properties}
   * — still wins when it is set, so a deployment can always overrule an integration.
   *
   * @param loggerName logger name for the durable narration listener, or {@code null} for none
   * @param defaultBufferCapacity ring size to use when configuration names none
   * @return A ready pipeline. Never {@code null}.
   * @throws IllegalArgumentException when the capacity is outside the buffer's supported range
   */
  public static EventPipeline createDefault(String loggerName, int defaultBufferCapacity) {
    return new PipelineBootstrap().build(loggerName, defaultBufferCapacity);
  }

  /**
   * Builds the pipeline this deployment has asked for.
   *
   * @param loggerName logger name for the durable narration listener, or {@code null} for none
   * @return A ready pipeline. Never {@code null}.
   * @throws IllegalStateException when a named topology has no discovered factory
   */
  public EventPipeline build(String loggerName) {
    return build(loggerName, BufferedEventConsumer.DEFAULT_CAPACITY);
  }

  /**
   * Builds the pipeline this deployment has asked for, with the caller's ring size as the default.
   *
   * @param loggerName logger name for the durable narration listener, or {@code null} for none
   * @param defaultBufferCapacity ring size to use when configuration names none
   * @return A ready pipeline. Never {@code null}.
   * @throws IllegalArgumentException when the capacity is outside the buffer's supported range
   * @throws IllegalStateException when a named topology has no discovered factory
   */
  public EventPipeline build(String loggerName, int defaultBufferCapacity) {
    if (defaultBufferCapacity < 1 || defaultBufferCapacity > BoundedEventBuffer.MAX_CAPACITY) {
      throw new IllegalArgumentException(
          "defaultBufferCapacity must be between 1 and "
              + BoundedEventBuffer.MAX_CAPACITY
              + ", but was "
              + defaultBufferCapacity);
    }
    var durableListener = narrationListener(loggerName);
    var listeners = registry.load(TraceEventListener.class);
    var strategy = config.resolve(STRATEGY_KEY, "").trim();
    return strategy.isEmpty()
        ? defaultTopology(durableListener, listeners, defaultBufferCapacity)
        : namedTopology(strategy, durableListener, listeners);
  }

  /** Dual-path, allocating the fan-out composite only when something was actually discovered. */
  private EventPipeline defaultTopology(
      Consumer<TraceEvent> durableListener,
      List<TraceEventListener> listeners,
      int defaultBufferCapacity) {
    var retention = new BufferedEventConsumer(bufferCapacity(defaultBufferCapacity), false);
    return new DualPathPipeline(
        durableListener,
        listeners.isEmpty() ? retention : new ListenerFanoutConsumer(retention, listeners));
  }

  /**
   * Resolves the configured ring size, degrading to the default rather than refusing to start.
   *
   * <p><b>@edgeCase</b> A non-numeric, zero, negative, or unrepresentably large value falls back to
   * {@link BufferedEventConsumer#DEFAULT_CAPACITY}. Unlike an unknown topology — which fails
   * initialization because it would silently change the durability guarantee — a mis-sized buffer
   * changes only how much analysis history survives a drain stall, and failing application startup
   * over a typo in an observability knob is the worse outcome.
   */
  private int bufferCapacity(int fallback) {
    var configured = config.resolve(CAPACITY_KEY, "").trim();
    if (configured.isEmpty()) {
      return fallback;
    }
    try {
      var requested = Integer.parseInt(configured);
      return requested >= 1 && requested <= BoundedEventBuffer.MAX_CAPACITY ? requested : fallback;
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private EventPipeline namedTopology(
      String strategy, Consumer<TraceEvent> durableListener, List<TraceEventListener> listeners) {
    var factory =
        registry.load(EventPipelineFactory.class).stream()
            .filter(candidate -> strategy.equalsIgnoreCase(candidate.name()))
            .findFirst()
            .orElseThrow(() -> unknownStrategy(strategy));
    var pipeline =
        factory.create(new PipelineSpec(durableListener, listeners, settingsFor(strategy)));
    if (pipeline == null) {
      throw new IllegalStateException(
          "EventPipelineFactory '" + strategy + "' returned no pipeline");
    }
    return pipeline;
  }

  private static IllegalStateException unknownStrategy(String strategy) {
    return new IllegalStateException(
        "No EventPipelineFactory named '"
            + strategy
            + "' was discovered, but "
            + STRATEGY_KEY
            + " requested it. Add the module providing that topology to the classpath, or remove "
            + "the property to use the default dual-path topology.");
  }

  /** Namespaced view so a strategy reads its own keys and core never parses them. */
  private PipelineSpec.Settings settingsFor(String strategy) {
    var prefix = STRATEGY_KEY + "." + strategy + ".";
    return (key, defaultValue) -> config.resolve(prefix + key, defaultValue);
  }

  /**
   * Resolves the SLF4J narration listener reflectively — core carries no slf4j dependency, and its
   * absence is an ordinary outcome, not a failure.
   *
   * <p><b>@edgeCase</b> Two outcomes, deliberately told apart. The class not being there is the
   * ordinary one and says nothing. Anything else — a static initialiser that throws ({@code
   * ExceptionInInitializerError}), a shaded or version-mismatched slf4j ({@code LinkageError}), a
   * listener without the expected constructor — is a misconfiguration that deserves one line on
   * stderr, and then narration is simply off. Neither may fail the caller: this runs while an
   * application builds its context or the agent initialises, and an optional observability jar has
   * no business stopping either.
   */
  @SuppressWarnings({"PMD.AvoidCatchingThrowable", "unchecked"})
  private Consumer<TraceEvent> narrationListener(String loggerName) {
    if (loggerName == null || loggerName.isEmpty() || narrationVetoed()) {
      return null;
    }
    try {
      var listenerClass = Class.forName(narrationListenerClass);
      var constructor = listenerClass.getConstructor(String.class);
      return (Consumer<TraceEvent>) constructor.newInstance(loggerName);
    } catch (ClassNotFoundException absent) {
      return null; // slf4j module absent — the default topology simply does not narrate
    } catch (Throwable t) { // NOPMD
      System.err.println( // NOPMD
          "narrative-trace: narration listener "
              + narrationListenerClass
              + " could not be created; continuing without narration ("
              + t
              + ")");
      return null;
    }
  }

  private boolean narrationVetoed() {
    return OFF.equalsIgnoreCase(config.resolve(NARRATION_KEY, "").trim());
  }
}
