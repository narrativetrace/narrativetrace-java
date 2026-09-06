/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.benchmarks;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.NoopNarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/**
 * What one traced call costs a context at each tracing level, end to end.
 *
 * <p><b>What one operation is, since 2026-08-31.</b> One operation is {@code enterMethod} + {@code
 * exitMethodWithReturn} + {@code captureTrace} + {@code reset} on <em>one</em> context — the whole
 * cycle of a one-node trace, with every part of it measured. The {@code reset()} is inside the
 * benchmark method on purpose; it is not a fixture.
 *
 * <p><b>Why the reset is in the body rather than batched away.</b> Until 2026-08-31 it was an
 * {@code @Setup(Level.Invocation)} fixture, resetting all five contexts, which JMH excludes from
 * the time but the GC profiler counts: 1,680 B of other contexts' bookkeeping was added to every
 * B/op figure this class published. Batching the calls and resetting once per batch — what {@link
 * ProxyOverheadBenchmark} does — would not work here, because these rows capture: the <em>k</em>-th
 * capture in a batch would filter <em>k</em> spans' events, so the row would measure a growing
 * trace instead of the one-node trace it is named for. Resetting per operation keeps the trace one
 * node deep, which is the thing being measured, and counts what that costs.
 *
 * <p><b>@edgeCase</b> Comparability with baselines taken before 2026-08-31 is broken by design.
 * Those rows carried five resets of other contexts and no reset of their own; these carry one, of
 * their own, inside the measurement. The history is preserved in the 2026-08-31
 * performance-regression analysis (private) and in the dated baseline files.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class ContextOverheadBenchmark {

  private static final MethodSignature SIGNATURE =
      new MethodSignature("TestService", "execute", List.of(), null, null);

  private NarrativeContext detailContext;
  private NarrativeContext narrativeContext;
  private NarrativeContext summaryContext;
  private NarrativeContext errorsContext;
  private NarrativeContext offContext;

  @Setup(Level.Trial)
  public void setup() {
    detailContext = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.DETAIL));
    narrativeContext =
        new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.NARRATIVE));
    summaryContext =
        new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.SUMMARY));
    errorsContext = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.ERRORS));
    offContext = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.OFF));
  }

  /** Starts every iteration on contexts that hold nothing, whatever the previous one left. */
  @Setup(Level.Iteration)
  public void resetContexts() {
    detailContext.reset();
    narrativeContext.reset();
    summaryContext.reset();
    errorsContext.reset();
    offContext.reset();
  }

  @Benchmark
  public void context_enterExit_DETAIL(Blackhole bh) {
    detailContext.enterMethod(SIGNATURE);
    detailContext.exitMethodWithReturn("result");
    bh.consume(detailContext.captureTrace());
    detailContext.reset();
  }

  @Benchmark
  public void context_enterExit_NARRATIVE(Blackhole bh) {
    narrativeContext.enterMethod(SIGNATURE);
    narrativeContext.exitMethodWithReturn("result");
    bh.consume(narrativeContext.captureTrace());
    narrativeContext.reset();
  }

  @Benchmark
  public void context_enterExit_SUMMARY(Blackhole bh) {
    summaryContext.enterMethod(SIGNATURE);
    summaryContext.exitMethodWithReturn("result");
    bh.consume(summaryContext.captureTrace());
    summaryContext.reset();
  }

  @Benchmark
  public void context_enterExit_ERRORS(Blackhole bh) {
    errorsContext.enterMethod(SIGNATURE);
    errorsContext.exitMethodWithReturn("result");
    bh.consume(errorsContext.captureTrace());
    errorsContext.reset();
  }

  @Benchmark
  public void context_enterExit_OFF(Blackhole bh) {
    offContext.enterMethod(SIGNATURE);
    offContext.exitMethodWithReturn("result");
    bh.consume(offContext.captureTrace());
    offContext.reset();
  }

  @Benchmark
  public void context_enterExit_NOOP(Blackhole bh) {
    NoopNarrativeContext.INSTANCE.enterMethod(SIGNATURE);
    NoopNarrativeContext.INSTANCE.exitMethodWithReturn("result");
    bh.consume(NoopNarrativeContext.INSTANCE.captureTrace());
    NoopNarrativeContext.INSTANCE.reset();
  }
}
