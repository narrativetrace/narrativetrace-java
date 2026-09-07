/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.config;

import ai.narrativetrace.api.config.TracingLevel;

/**
 * Mutable holder for the active {@link TracingLevel}.
 *
 * <p>The level is stored in a volatile field, so callers that share one config instance across
 * multiple contexts or threads can change capture behavior at runtime without additional locking.
 *
 * <pre>{@code
 * var config = new NarrativeTraceConfig(TracingLevel.NARRATIVE);
 * var context = new ThreadLocalNarrativeContext(config);
 *
 * // Change capture behavior at runtime
 * config.setLevel(TracingLevel.DETAIL);
 * }</pre>
 *
 * @see TracingLevel
 * @see ai.narrativetrace.core.context.ThreadLocalNarrativeContext
 */
public final class NarrativeTraceConfig {

  private volatile TracingLevel level;

  // Capture-width flags (owner decision 2026-08-15: the client decides, via the ConfigResolver
  // chain). Flags gate CAPTURE only, never schema shape: gated fields stay nullable and are
  // simply absent when disabled, so consumers and fixtures never branch on configuration.
  private volatile boolean captureResource = true;
  private volatile boolean captureSourceLocation;
  private volatile boolean captureInstanceIds;

  /** Creates a config with the default level, {@link TracingLevel#DETAIL}. */
  public NarrativeTraceConfig() {
    this(TracingLevel.DETAIL);
  }

  /**
   * Creates a config with the specified tracing level.
   *
   * @param level the initial tracing level
   */
  public NarrativeTraceConfig(TracingLevel level) {
    this.level = level;
  }

  /**
   * Applies the {@code narrativetrace.capture.*} flags from the given resolver (system property
   * first, then {@code narrativetrace.properties}, then the built-in default).
   *
   * @param resolver the configuration resolver
   * @return this config, for chaining
   */
  public NarrativeTraceConfig resolveCaptureFlags(ConfigResolver resolver) {
    captureResource =
        Boolean.parseBoolean(resolver.resolve("narrativetrace.capture.resource", "true"));
    captureSourceLocation =
        Boolean.parseBoolean(resolver.resolve("narrativetrace.capture.sourceLocation", "false"));
    captureInstanceIds =
        Boolean.parseBoolean(resolver.resolve("narrativetrace.capture.instanceIds", "false"));
    return this;
  }

  /** Whether auto-detected process resource identity (host/pid/runtime) is stamped onto spans. */
  public boolean captureResource() {
    return captureResource;
  }

  /** Opt-out for hostname-sensitive clients; default on. */
  public void setCaptureResource(boolean captureResource) {
    this.captureResource = captureResource;
  }

  /** Whether source location (file/line) is captured; default off (proxy pays a walk per call). */
  public boolean captureSourceLocation() {
    return captureSourceLocation;
  }

  public void setCaptureSourceLocation(boolean captureSourceLocation) {
    this.captureSourceLocation = captureSourceLocation;
  }

  /** Whether per-object instance ids are captured; default off. */
  public boolean captureInstanceIds() {
    return captureInstanceIds;
  }

  public void setCaptureInstanceIds(boolean captureInstanceIds) {
    this.captureInstanceIds = captureInstanceIds;
  }

  /**
   * Returns the current tracing level.
   *
   * @return the active level
   */
  public TracingLevel level() {
    return level;
  }

  /**
   * Changes the tracing level. The volatile write makes the change immediately visible to all
   * threads sharing this config instance.
   *
   * @param level the new tracing level
   */
  public void setLevel(TracingLevel level) {
    this.level = level;
  }
}
