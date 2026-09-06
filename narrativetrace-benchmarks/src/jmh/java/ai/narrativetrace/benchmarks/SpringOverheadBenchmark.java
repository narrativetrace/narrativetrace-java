/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.benchmarks;

import ai.narrativetrace.benchmarks.services.NarratedInterpolatedServiceImpl;
import ai.narrativetrace.benchmarks.services.PlainServiceImpl;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.spring.NarrativeTraceBeanPostProcessor;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class SpringOverheadBenchmark {

  @Configuration
  static class BenchmarkConfig {
    @Bean
    public NarrativeContext narrativeContext() {
      return new ThreadLocalNarrativeContext();
    }

    @Bean
    public NarrativeTraceBeanPostProcessor narrativeTraceBeanPostProcessor(
        NarrativeContext context) {
      return new NarrativeTraceBeanPostProcessor(
          context, List.of("ai.narrativetrace.benchmarks.services"));
    }

    @Bean
    public PlainService plainService() {
      return new PlainServiceImpl();
    }

    @Bean
    public NarratedInterpolatedService narratedInterpolatedService() {
      return new NarratedInterpolatedServiceImpl();
    }
  }

  private PlainService springPlain;
  private NarratedInterpolatedService springNarrated;
  private NarrativeContext context;

  @Setup(Level.Trial)
  public void setup() {
    var ctx = new AnnotationConfigApplicationContext(BenchmarkConfig.class);
    springPlain = ctx.getBean(PlainService.class);
    springNarrated = ctx.getBean(NarratedInterpolatedService.class);
    context = ctx.getBean(NarrativeContext.class);
  }

  @Setup(Level.Invocation)
  public void resetContext() {
    context.reset();
  }

  @Benchmark
  public void spring_noAnnotations(Blackhole bh) {
    bh.consume(springPlain.execute("test"));
  }

  @Benchmark
  public void spring_narrated_interpolated(Blackhole bh) {
    bh.consume(springNarrated.execute("test"));
  }
}
