/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

/**
 * Reads the retention seams B-211 added to {@link ThreadLocalNarrativeContext}. They are
 * package-private on purpose — retention is invisible from the public surface — so the soak reads
 * them from a probe in the same package, in this test-only module.
 */
public final class SoakProbe {

  private SoakProbe() {}

  /** Span contexts the context still holds, across every thread. */
  public static int retainedSpans(ThreadLocalNarrativeContext context) {
    return context.retainedSpanCount();
  }

  /** Spans the CALLING thread's own stack still knows. */
  public static int knownSpansOnThisThread(ThreadLocalNarrativeContext context) {
    return context.knownSpanCount();
  }

  /**
   * A context whose unowned-span limit is {@code limit} — the B-211 test seam (a package-private
   * constructor), so the soak crosses the release boundary many times over in seconds.
   */
  public static ThreadLocalNarrativeContext contextWithUnownedLimit(
      ai.narrativetrace.core.config.NarrativeTraceConfig config,
      ai.narrativetrace.core.pipeline.EventPipeline pipeline,
      int limit) {
    return new ThreadLocalNarrativeContext(config, pipeline, null, limit);
  }

  /** The production limit above which a context releases the oldest spans no stack owns. */
  public static int unownedSpanLimit() {
    return ThreadLocalNarrativeContext.DEFAULT_UNOWNED_SPAN_LIMIT;
  }
}
