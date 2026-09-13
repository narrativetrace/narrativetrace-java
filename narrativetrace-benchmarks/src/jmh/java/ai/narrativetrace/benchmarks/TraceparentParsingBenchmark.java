/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.benchmarks;

import ai.narrativetrace.api.event.Traceparent;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * {@code traceparent} parsing cost on an attacker-controlled header — home for a genuine
 * performance guard that used to live as a wall-clock assertion in {@code
 * narrativetrace-security-tests}' Tier A suite ({@code Oracles.withinBudget}, removed 2026-09-13:
 * family release rule 3, wall-clock/GC/scheduler are never test inputs).
 *
 * <p>INTENT: The hostile corpus ({@code hostile-corpus/headers.json}, id {@code very-long-fields})
 * carries a version-01 header with a hundred thousand extension fields — valid per the W3C spec, so
 * it must be <b>accepted</b>, which {@code
 * TraceparentParsingPropertyTest#everyCorpusHeaderParsesToTheOutcomeItDeclares} already pins with
 * no clock involved. What the removed assertion actually guarded beyond acceptance was parse
 * <em>cost</em>: a naive field-splitting implementation could go quadratic on a hundred thousand
 * fields, and that is a genuine algorithmic-complexity concern — every {@code traceparent} header
 * this library reads arrives from a stranger — not reducible to a bounded-<em>output</em> property
 * the way a renderer's truncating caps are.
 *
 * <p>This lane is the family's answer to "assert a performance property without wall-clock ever
 * being a pass/fail input": JMH measures throughput here, and a regression is judged the way this
 * module's other benchmarks already are — by comparing this run's numbers against a saved baseline
 * file in this directory (see {@code baseline.txt}), never against an absolute cutoff. Deliberately
 * not wired into {@code check} — this module carries no test sources and runs on a schedule, like
 * every other benchmark here.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class TraceparentParsingBenchmark {

  /**
   * Mirrors the hostile corpus's {@code very-long-fields} case exactly (prefix, repeat unit and
   * count) — kept as a literal here rather than read from the shared corpus fixture, since this
   * module has no path to {@code narrativetrace-security-tests}' test-only corpus loader.
   */
  private static final String VERY_LONG_FIELDS =
      "01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01" + "-x".repeat(100_000);

  @Benchmark
  public void parse_hundredThousandExtensionFields(Blackhole bh) {
    bh.consume(Traceparent.parse(VERY_LONG_FIELDS));
  }
}
