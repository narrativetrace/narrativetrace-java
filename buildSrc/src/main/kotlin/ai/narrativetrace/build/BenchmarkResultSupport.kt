/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import groovy.json.JsonSlurper
import java.io.File

/**
 * One benchmark's reading out of a JMH `-rf json` result file.
 *
 * @param benchmark fully qualified benchmark name, e.g. `ai.narrativetrace.benchmarks.X.y`
 * @param mode JMH mode as recorded (`avgt`, `thrpt`, …)
 * @param score primary metric score in [unit]
 * @param unit primary metric unit, e.g. `ns/op`
 * @param allocationNorm `gc.alloc.rate.norm` in B/op, or `null` when the run carried no GC profiler
 */
data class BenchmarkScore(
    val benchmark: String,
    val mode: String,
    val score: Double,
    val unit: String,
    val allocationNorm: Double?
) {
    /** Trailing `Class.method`, which is how the human-readable baselines name a benchmark. */
    val shortName: String
        get() = benchmark.split(".").takeLast(2).joinToString(".")
}

/**
 * INTENT: The single reader of JMH's JSON output, shared by the allocation gate and the throughput
 * comparison so the two can never disagree about what a result file says.
 *
 * JMH's schema is stable and small: an array of objects, each with `benchmark`, `mode`,
 * `primaryMetric.{score,scoreUnit}` and a `secondaryMetrics` map that carries the profiler results.
 * The GC profiler contributes `·gc.alloc.rate.norm` — note the leading U+00B7 middle dot, which JMH
 * puts on every secondary metric name.
 */
object BenchmarkResultSupport {

    /** JMH's name for normalized allocation, middle dot included. */
    const val ALLOCATION_METRIC = "·gc.alloc.rate.norm"

    /** Reads every benchmark in a JMH JSON result file, in file order. */
    @Suppress("UNCHECKED_CAST")
    fun read(file: File): List<BenchmarkScore> {
        if (!file.isFile) {
            throw IllegalArgumentException("No JMH result file at ${file.absolutePath}")
        }
        val entries = JsonSlurper().parseText(file.readText()) as List<Map<String, Any?>>
        return entries.map { entry -> scoreOf(entry) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun scoreOf(entry: Map<String, Any?>): BenchmarkScore {
        val primary = entry["primaryMetric"] as Map<String, Any?>
        val secondary = entry["secondaryMetrics"] as? Map<String, Any?> ?: emptyMap()
        val allocation = secondary[ALLOCATION_METRIC] as? Map<String, Any?>
        return BenchmarkScore(
            benchmark = entry["benchmark"] as String,
            mode = entry["mode"] as String,
            score = (primary["score"] as Number).toDouble(),
            unit = primary["scoreUnit"] as String,
            allocationNorm = (allocation?.get("score") as? Number)?.toDouble()
        )
    }
}
