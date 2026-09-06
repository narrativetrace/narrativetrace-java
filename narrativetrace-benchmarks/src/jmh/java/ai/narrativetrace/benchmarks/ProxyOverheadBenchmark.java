/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.benchmarks;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.NoopNarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/**
 * What a call through a tracing proxy costs, against the same call made directly.
 *
 * <p><b>What one operation is, since 2026-08-31.</b> One operation is <em>one call</em>. The
 * benchmark methods call {@code BATCH} times and are annotated {@link OperationsPerInvocation}, so
 * JMH divides both the time and the allocation by {@code BATCH}. The rows that drive an active
 * context also call {@code reset()} once per batch, <em>inside the measured method</em>, so its
 * cost is counted — amortised at 1/{@code BATCH} of a reset per operation — rather than hidden.
 * {@code directCall}, {@code proxy_OFF} and {@code proxy_noopContext} reset nothing because they
 * accumulate nothing: an OFF context is never entered, and the no-op context holds no state.
 *
 * <p><b>Why the batch exists.</b> The reset has to happen somewhere. Until 2026-08-31 it was an
 * {@code @Setup(Level.Invocation)} fixture, which JMH excludes from the time but the GC profiler
 * counts — so every B/op figure this class produced included two {@code reset()} calls, and when
 * {@code reset()} regressed from ~0 B to ~336 B the published "allocation per traced call" numbers
 * silently gained 672 B they did not own. Moving the reset to {@code Level.Iteration} alone would
 * be worse than the disease: at ~1 µs/op a one-second iteration is ~10^6 calls, and a context that
 * kept every one of their spans would grow its span map and its event ring without bound, shed
 * events, and end up measuring the collapse rather than the call. The batch bounds it: {@code
 * BATCH} spans and 2·{@code BATCH} events accumulate, ~3 % of the ring, and then they are cleared
 * as part of the measured work.
 *
 * <p><b>@edgeCase</b> Comparability with baselines taken before 2026-08-31 is broken by design.
 * Those rows measured a call <em>plus</em> two resets; these measure a call. The history is
 * preserved in `planning/perf-regression-2026-08-31.md` and in the dated baseline files.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class ProxyOverheadBenchmark {

  /**
   * Calls per measured invocation.
   *
   * <p>Large enough that one {@code reset()} divides away to fractions of a byte per operation,
   * small enough that the state it clears stays far from every ceiling: 1,000 spans against the
   * 10,000-span adoption cap, and 2,000 events against a 65,536-slot ring that sheds at 70 % fill.
   */
  private static final int BATCH = 1_000;

  private PlainService directPlain;
  private PlainService proxyPlain;
  private NarratedStaticService proxyNarratedStatic;
  private NarratedInterpolatedService proxyNarratedInterpolated;
  private RedactedService proxyRedacted;
  private SummaryService proxySummary;
  private ErrorService proxyError;
  private PlainService proxyNoop;
  private PlainService proxyOff;

  private NarrativeContext detailContext;
  private NarrativeContext offContext;

  @Setup(Level.Trial)
  public void setup() {
    detailContext = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.DETAIL));
    offContext = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.OFF));

    PlainService plainImpl = input -> "result:" + input;

    directPlain = plainImpl;
    proxyPlain = NarrativeTraceProxy.trace(plainImpl, PlainService.class, detailContext);

    proxyNarratedStatic =
        NarrativeTraceProxy.trace(
            (NarratedStaticService) input -> "result:" + input,
            NarratedStaticService.class,
            detailContext);

    proxyNarratedInterpolated =
        NarrativeTraceProxy.trace(
            (NarratedInterpolatedService) input -> "result:" + input,
            NarratedInterpolatedService.class,
            detailContext);

    proxyRedacted =
        NarrativeTraceProxy.trace(
            (RedactedService) input -> "result:" + input, RedactedService.class, detailContext);

    proxySummary =
        NarrativeTraceProxy.trace(
            (SummaryService) input -> new SummaryResult(input),
            SummaryService.class,
            detailContext);

    proxyError =
        NarrativeTraceProxy.trace(
            (ErrorService) input -> "result:" + input, ErrorService.class, detailContext);

    proxyNoop =
        NarrativeTraceProxy.trace(plainImpl, PlainService.class, NoopNarrativeContext.INSTANCE);

    proxyOff = NarrativeTraceProxy.trace(plainImpl, PlainService.class, offContext);
  }

  /** Starts every iteration on contexts that hold nothing, whatever the previous one left. */
  @Setup(Level.Iteration)
  public void resetContexts() {
    detailContext.reset();
    offContext.reset();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH)
  public void directCall(Blackhole bh) {
    for (int i = 0; i < BATCH; i++) {
      bh.consume(directPlain.execute("test"));
    }
  }

  @Benchmark
  @OperationsPerInvocation(BATCH)
  public void proxy_noAnnotations(Blackhole bh) {
    for (int i = 0; i < BATCH; i++) {
      bh.consume(proxyPlain.execute("test"));
    }
    detailContext.reset();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH)
  public void proxy_narrated_static(Blackhole bh) {
    for (int i = 0; i < BATCH; i++) {
      bh.consume(proxyNarratedStatic.execute("test"));
    }
    detailContext.reset();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH)
  public void proxy_narrated_interpolated(Blackhole bh) {
    for (int i = 0; i < BATCH; i++) {
      bh.consume(proxyNarratedInterpolated.execute("test"));
    }
    detailContext.reset();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH)
  public void proxy_notTraced(Blackhole bh) {
    for (int i = 0; i < BATCH; i++) {
      bh.consume(proxyRedacted.execute("test"));
    }
    detailContext.reset();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH)
  public void proxy_narrativeSummary(Blackhole bh) {
    for (int i = 0; i < BATCH; i++) {
      bh.consume(proxySummary.execute("test"));
    }
    detailContext.reset();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH)
  public void proxy_onError(Blackhole bh) {
    for (int i = 0; i < BATCH; i++) {
      bh.consume(proxyError.execute("test"));
    }
    detailContext.reset();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH)
  public void proxy_noopContext(Blackhole bh) {
    for (int i = 0; i < BATCH; i++) {
      bh.consume(proxyNoop.execute("test"));
    }
  }

  @Benchmark
  @OperationsPerInvocation(BATCH)
  public void proxy_OFF(Blackhole bh) {
    for (int i = 0; i < BATCH; i++) {
      bh.consume(proxyOff.execute("test"));
    }
  }
}
