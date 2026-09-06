/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import java.io.File

/** One line of the comparison: baseline, current, the band, and whether it broke. */
data class BenchmarkComparison(
    val benchmark: String,
    val metric: String,
    val baseline: Double,
    val current: Double,
    val tolerancePercent: Double,
    val unit: String
) {
    /** Positive means worse than baseline; both metrics here are lower-is-better. */
    val changePercent: Double
        get() = if (baseline == 0.0) 0.0 else (current - baseline) / baseline * 100.0

    val regressed: Boolean
        get() = changePercent > tolerancePercent
}

/**
 * INTENT: Backs the `benchmarkCompare` task — reads a fresh JMH JSON result against the committed
 * `baseline.txt` and answers one question, in a table, with a verdict.
 *
 * Two rules shape it. **Faster never fails**: an improvement is not a regression, and a gate that
 * punished one would be gamed within a week. **Unknown never passes silently**: a benchmark in the
 * baseline that the run did not produce is reported, because a suite that quietly stopped measuring
 * something is the failure mode this whole tier exists to prevent.
 *
 * Bands come from `benchmark-tolerances.properties`, matched longest-key-first against the short
 * `Class.method` name so a specific benchmark can override the `default`.
 */
object BenchmarkCompareSupport {

    private const val DEFAULT_KEY = "default"
    private const val ALLOCATION_PREFIX = "allocation."

    /** `Class.method  avgt  5  1234.567 ± 8.9  ns/op` rows of a committed baseline file. */
    private val BASELINE_ROW =
        Regex("""^(\S+\.\S+)\s+(\w+)\s+\d+\s+([\d.]+)\s*±?\s*[\d.]*\s+(\S+)\s*$""")

    /** Reads the `Class.method -> score` rows a baseline file states, ignoring comments. */
    fun readBaseline(file: File): Map<String, Pair<Double, String>> {
        if (!file.isFile) {
            throw IllegalArgumentException("No baseline at ${file.absolutePath}")
        }
        return file.readLines()
            .filterNot { it.trimStart().startsWith("#") }
            .mapNotNull { BASELINE_ROW.find(it.trim()) }
            .associate { match ->
                match.groupValues[1] to (match.groupValues[3].toDouble() to match.groupValues[4])
            }
    }

    /** Reads `benchmark = percent` bands, plus the `default` and `allocation.default` fallbacks. */
    fun readTolerances(file: File): Map<String, Double> {
        if (!file.isFile) {
            throw IllegalArgumentException("No tolerance bands at ${file.absolutePath}")
        }
        return file.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.contains('=') }
            .associate { line ->
                val key = line.substringBefore('=').trim()
                val value = line.substringAfter('=').trim().toDoubleOrNull()
                    ?: throw IllegalArgumentException("Tolerance for $key is not a number: $line")
                key to value
            }
    }

    /** The band for one benchmark: its own key if present, else the metric's default. */
    fun toleranceFor(tolerances: Map<String, Double>, benchmark: String, allocation: Boolean): Double {
        val prefix = if (allocation) ALLOCATION_PREFIX else ""
        return tolerances[prefix + benchmark]
            ?: tolerances[prefix + DEFAULT_KEY]
            ?: tolerances[DEFAULT_KEY]
            ?: throw IllegalArgumentException("No tolerance band and no default for $benchmark")
    }

    /** Compares one run against one baseline, in baseline order. */
    fun compare(
        baseline: Map<String, Pair<Double, String>>,
        current: List<BenchmarkScore>,
        tolerances: Map<String, Double>
    ): List<BenchmarkComparison> {
        val byName = current.associateBy { it.shortName }
        return baseline.entries.mapNotNull { (benchmark, recorded) ->
            byName[benchmark]?.let { score ->
                BenchmarkComparison(
                    benchmark = benchmark,
                    metric = "time",
                    baseline = recorded.first,
                    current = score.score,
                    tolerancePercent = toleranceFor(tolerances, benchmark, allocation = false),
                    unit = recorded.second
                )
            }
        }
    }

    /** Benchmarks the baseline names that the run never produced — always reported. */
    fun missing(baseline: Map<String, Pair<Double, String>>, current: List<BenchmarkScore>): List<String> {
        val measured = current.map { it.shortName }.toSet()
        return baseline.keys.filterNot { measured.contains(it) }.sorted()
    }

    /** The problems, one line each, empty when nothing regressed and nothing went missing. */
    fun problems(comparisons: List<BenchmarkComparison>, missing: List<String>): List<String> =
        comparisons.filter { it.regressed }.map { comparison ->
            "%s: %.3f %s against a baseline of %.3f — %+.1f%%, outside the %.0f%% band".format(
                comparison.benchmark,
                comparison.current,
                comparison.unit,
                comparison.baseline,
                comparison.changePercent,
                comparison.tolerancePercent
            )
        } + missing.map { "$it: in the baseline, not in this run" }

    /** The table, printed whether the comparison passes or fails. */
    fun table(comparisons: List<BenchmarkComparison>, missing: List<String>): String {
        val width = (comparisons.map { it.benchmark.length } + missing.map { it.length } + listOf(9)).max()
        val header = "%-${width}s %14s %14s %9s %8s  %s"
            .format("Benchmark", "baseline", "current", "change", "band", "")
        val rows = comparisons.map { comparison ->
            "%-${width}s %14.3f %14.3f %+8.1f%% %7.0f%%  %s".format(
                comparison.benchmark,
                comparison.baseline,
                comparison.current,
                comparison.changePercent,
                comparison.tolerancePercent,
                if (comparison.regressed) "REGRESSED" else "ok"
            )
        }
        val absent = missing.map { "%-${width}s %14s %14s %9s %8s  %s".format(it, "—", "—", "—", "—", "MISSING") }
        return (listOf(header) + rows + absent).joinToString("\n")
    }
}
