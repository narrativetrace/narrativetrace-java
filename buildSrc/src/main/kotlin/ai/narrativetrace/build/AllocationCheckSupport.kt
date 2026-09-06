/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import java.io.File

/** One benchmark's verdict: what it allocated, what it is allowed to, and whether that is fine. */
data class AllocationVerdict(
    val benchmark: String,
    val measured: Double?,
    val threshold: Long
) {
    val overBudget: Boolean
        get() = measured == null || measured > threshold
}

/**
 * INTENT: Backs the `allocationCheck` task — turns `allocation-baseline.txt`'s prose invariant
 * ("these benchmarks must stay at or near their current B/op") into something that fails a build.
 *
 * The thresholds live in `allocation-thresholds.properties`, one file, read by this object and by
 * nothing else. That file is the only place a number may be changed, and changing one is a
 * deliberate act with a diff, which is the whole point: the previous arrangement documented the
 * invariants in a text file nothing read, and they had drifted unnoticed for six months.
 *
 * A benchmark named in the thresholds file but absent from the result file counts as over budget.
 * Silence is the failure mode this gate exists to prevent — a run that quietly stopped measuring
 * something must not read as a pass.
 */
object AllocationCheckSupport {

    /** Reads `benchmark = maxBytesPerOp` pairs, ignoring blank lines and `#` comments. */
    fun readThresholds(file: File): Map<String, Long> {
        if (!file.isFile) {
            throw IllegalArgumentException("No allocation thresholds at ${file.absolutePath}")
        }
        return file.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .associate { line -> parse(line) }
    }

    private fun parse(line: String): Pair<String, Long> {
        val separator = line.indexOf('=')
        require(separator > 0) { "Malformed threshold line (expected benchmark=bytes): $line" }
        val benchmark = line.substring(0, separator).trim()
        val bytes = line.substring(separator + 1).trim().toLongOrNull()
            ?: throw IllegalArgumentException("Threshold for $benchmark is not a number: $line")
        return benchmark to bytes
    }

    /** Pairs every threshold with what the run measured, in threshold-file order. */
    fun verdicts(thresholds: Map<String, Long>, measured: List<BenchmarkScore>): List<AllocationVerdict> {
        val byName = measured.associateBy { it.benchmark }
        return thresholds.map { (benchmark, threshold) ->
            AllocationVerdict(benchmark, byName[benchmark]?.allocationNorm, threshold)
        }
    }

    /** The problems, one line each, empty when every benchmark is inside its threshold. */
    fun problems(verdicts: List<AllocationVerdict>): List<String> =
        verdicts.filter { it.overBudget }.map { verdict ->
            if (verdict.measured == null) {
                "${short(verdict.benchmark)}: not measured — the run produced no gc.alloc.rate.norm for it"
            } else {
                "${short(verdict.benchmark)}: ${format(verdict.measured)} B/op exceeds " +
                    "${verdict.threshold} B/op"
            }
        }

    /** A table of every threshold and its reading, printed whether the check passes or fails. */
    fun table(verdicts: List<AllocationVerdict>): String {
        val width = verdicts.maxOfOrNull { short(it.benchmark).length } ?: 0
        val header = "%-${width}s %12s %12s  %s".format("Benchmark", "B/op", "max B/op", "")
        val rows = verdicts.map { verdict ->
            "%-${width}s %12s %12d  %s".format(
                short(verdict.benchmark),
                verdict.measured?.let { format(it) } ?: "—",
                verdict.threshold,
                if (verdict.overBudget) "OVER" else "ok"
            )
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun short(benchmark: String) = benchmark.split(".").takeLast(2).joinToString(".")

    private fun format(bytes: Double) =
        if (bytes == Math.floor(bytes)) bytes.toLong().toString() else "%.1f".format(bytes)
}
