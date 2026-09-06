/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.spi;

import ai.narrativetrace.api.spi.ReportContributor;
import ai.narrativetrace.api.spi.TraceEventListener;
import ai.narrativetrace.core.config.ConfigResolver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Discovers extension providers declared through {@link ServiceLoader}.
 *
 * <p>INTENT: The one place discovery happens, so the failure and opt-out semantics are defined once
 * instead of at every extension point. Discovery is separate from activation: this class answers
 * "what is on the classpath", and the caller decides what that means. Additive extension points are
 * active by presence; a replacement strategy is only ever looked up after configuration names it.
 *
 * <p><b>@llmNote</b> Results are cached per service type per registry instance, so the classpath is
 * scanned once rather than on any hot path. A registry is cheap; the composition root holds one.
 *
 * <p><b>@sideEffects</b> Failures are isolated per provider: a provider that cannot be loaded or
 * constructed is reported once on stderr and skipped, leaving the rest of the discovery pass
 * intact. Discovery never throws on behalf of a broken extension.
 *
 * @see TraceEventListener
 * @see ReportContributor
 */
public final class ExtensionRegistry {

  /** Kill switch: {@code off} disables discovery entirely, for hardened deployments. */
  static final String DISCOVERY_KEY = "narrativetrace.discovery";

  /** Comma-separated provider class names to skip, for surgical exclusion. */
  static final String DISABLED_KEY = "narrativetrace.discovery.disabled";

  private static final String OFF = "off";

  /** Safety valve: stop iterating if a loader keeps failing without making progress. */
  private static final int MAX_CONSECUTIVE_FAILURES = 16;

  private final ConfigResolver config;
  private final ClassLoader explicitLoader;
  private final Map<Class<?>, List<?>> cache = new ConcurrentHashMap<>();

  /** Creates a registry resolving configuration and providers from the thread context loader. */
  public ExtensionRegistry() {
    this(new ConfigResolver(), null);
  }

  /**
   * Creates a registry with explicit configuration, discovering through the context class loader.
   *
   * @param config resolver for the discovery opt-out keys
   */
  public ExtensionRegistry(ConfigResolver config) {
    this(config, null);
  }

  /**
   * Creates a registry with explicit configuration and an explicit discovery loader.
   *
   * @param config resolver for the discovery opt-out keys
   * @param explicitLoader Loader to discover through, or {@code null} to use the thread context
   *     loader and fall back to this class's own loader (agent-attach scenarios, where the context
   *     loader may not see the extension jars).
   */
  public ExtensionRegistry(ConfigResolver config, ClassLoader explicitLoader) {
    if (config == null) {
      throw new IllegalArgumentException("config must not be null");
    }
    this.config = config;
    this.explicitLoader = explicitLoader;
  }

  /**
   * Returns every usable provider of the given service type.
   *
   * @param <T> the service type
   * @param serviceType the extension point to discover implementations of
   * @return Immutable list of instantiated providers, empty when none are declared or discovery is
   *     switched off. Stable across calls: the same list instance is returned for a given type.
   */
  @SuppressWarnings("unchecked")
  public <T> List<T> load(Class<T> serviceType) {
    if (serviceType == null) {
      throw new IllegalArgumentException("serviceType must not be null");
    }
    return (List<T>) cache.computeIfAbsent(serviceType, this::discover);
  }

  private <T> List<T> discover(Class<T> serviceType) {
    if (discoveryDisabled()) {
      return List.of();
    }
    var providers =
        collect(ServiceLoader.load(serviceType, loaderFor(serviceType)), disabledNames());
    assert providers != null : "discovery must return a list, never null";
    return providers;
  }

  private boolean discoveryDisabled() {
    return OFF.equalsIgnoreCase(config.resolve(DISCOVERY_KEY, "").trim());
  }

  private Set<String> disabledNames() {
    return Arrays.stream(config.resolve(DISABLED_KEY, "").split(","))
        .map(String::trim)
        .filter(name -> !name.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }

  private ClassLoader loaderFor(Class<?> serviceType) {
    if (explicitLoader != null) {
      return explicitLoader;
    }
    var contextLoader = Thread.currentThread().getContextClassLoader();
    return contextLoader != null ? contextLoader : serviceType.getClassLoader();
  }

  /**
   * Iterates providers, skipping disabled and broken ones without abandoning the pass.
   *
   * <p><b>@edgeCase</b> A class named in a declaration but missing or unconstructable costs only
   * that provider. A <em>syntactically</em> invalid declaration line is different: the JDK parser
   * cannot resume past it, so the remainder of that file is lost. Discovery still returns rather
   * than throwing, and the loop's failure budget stops it from spinning on a wedged iterator.
   */
  private <T> List<T> collect(ServiceLoader<T> services, Set<String> disabled) {
    var found = new ArrayList<T>();
    var iterator = services.stream().iterator();
    int consecutiveFailures = 0;
    while (consecutiveFailures < MAX_CONSECUTIVE_FAILURES) {
      ServiceLoader.Provider<T> provider;
      try {
        if (!iterator.hasNext()) {
          break;
        }
        provider = iterator.next();
      } catch (ServiceConfigurationError e) {
        if (consecutiveFailures == 0) {
          warn("extension declaration could not be read", e);
        }
        consecutiveFailures++;
        continue;
      }
      consecutiveFailures = 0;
      instantiate(provider, disabled).ifPresent(found::add);
    }
    return List.copyOf(found);
  }

  /**
   * Constructs one discovered extension, or reports why it could not be.
   *
   * <p><b>@edgeCase</b> {@link Throwable}, not {@code Exception | ServiceConfigurationError}: an
   * extension's static initialiser failing raises {@code ExceptionInInitializerError} and a shaded
   * or mismatched dependency raises {@code LinkageError}. Discovery runs while the application is
   * building its context, so either one used to be a startup failure caused by an optional
   * observability jar.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // a broken extension must not break discovery
  private <T> Optional<T> instantiate(ServiceLoader.Provider<T> provider, Set<String> disabled) {
    var className = provider.type().getName();
    if (disabled.contains(className)) {
      return Optional.empty();
    }
    try {
      return Optional.of(provider.get());
    } catch (Throwable t) { // NOPMD
      warn("extension provider " + className + " could not be constructed", t);
      return Optional.empty();
    }
  }

  private static void warn(String message, Throwable cause) {
    System.err.println( // NOPMD
        "narrative-trace: " + message + " — disabled for this JVM (" + cause + ")");
  }
}
