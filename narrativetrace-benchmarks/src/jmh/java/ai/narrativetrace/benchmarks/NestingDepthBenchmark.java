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
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class NestingDepthBenchmark {

  private NestingService proxy;
  private NarrativeContext context;

  @Setup(Level.Trial)
  public void setup() {
    context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.DETAIL));

    // Self-referencing proxy: each call decrements depth and calls itself
    var holder = new NestingService[1];
    NestingService impl =
        depth -> {
          if (depth <= 1) {
            return "leaf";
          }
          return holder[0].call(depth - 1);
        };
    holder[0] = NarrativeTraceProxy.trace(impl, NestingService.class, context);
    proxy = holder[0];
  }

  @Setup(Level.Invocation)
  public void resetContext() {
    context.reset();
  }

  @Benchmark
  public void nesting_depth_1(Blackhole bh) {
    bh.consume(proxy.call(1));
  }

  @Benchmark
  public void nesting_depth_10(Blackhole bh) {
    bh.consume(proxy.call(10));
  }

  @Benchmark
  public void nesting_depth_50(Blackhole bh) {
    bh.consume(proxy.call(50));
  }
}
