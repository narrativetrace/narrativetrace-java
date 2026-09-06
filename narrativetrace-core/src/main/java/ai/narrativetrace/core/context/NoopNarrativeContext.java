/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.SpanIdGenerator;
import ai.narrativetrace.api.event.TraceId;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.List;

/**
 * Singleton {@link NarrativeContext} that discards all capture calls.
 *
 * <p>INTENT: Use this when tracing is disabled but callers still expect a concrete context object.
 * Every method is side-effect free and {@link #captureTrace()} always returns an empty tree.
 */
public final class NoopNarrativeContext implements NarrativeContext {

  public static final NoopNarrativeContext INSTANCE = new NoopNarrativeContext();

  private static final TraceTree EMPTY_TREE = new DefaultTraceTree(List.of());

  private NoopNarrativeContext() {}

  @Override
  public boolean isActive() {
    return false;
  }

  @Override
  public SpanId enterMethod(MethodSignature signature) {
    return null;
  }

  @Override
  public void detachFrame(SpanId spanId) {}

  @Override
  public void exitMethodWithReturn(String renderedReturnValue) {}

  @Override
  public void exitMethodWithReturn(String renderedReturnValue, SpanId spanId) {}

  @Override
  public void exitMethodWithException(Throwable exception, String errorContext) {}

  @Override
  public void exitMethodWithException(Throwable exception, String errorContext, SpanId spanId) {}

  @Override
  public TraceTree captureTrace() {
    return EMPTY_TREE;
  }

  @Override
  public TraceId traceId() {
    return SpanIdGenerator.traceId();
  }

  @Override
  public void reset() {}

  @Override
  public ContextSnapshot snapshot() {
    return NoopContextSnapshot.INSTANCE;
  }

  private static final class NoopContextSnapshot implements ContextSnapshot {
    static final NoopContextSnapshot INSTANCE = new NoopContextSnapshot();
    private static final ContextScope NOOP_SCOPE = () -> {};

    @Override
    public ContextScope activate() {
      return NOOP_SCOPE;
    }
  }
}
