/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import java.io.File

/**
 * INTENT: Backs the root `fuzz` aggregate — makes a green coverage-guided fuzz run mean that every
 * target was actually fuzzed.
 *
 * Jazzer's JUnit engine fuzzes at most **one** `@FuzzTest` per JVM: the first one it reaches runs
 * for its budget and every other one is abandoned with "Only a single fuzz test should be executed
 * per fuzzing run". JUnit records that as a skip, Gradle records the skip as a passing test task,
 * and the build says SUCCESS. A 2026-09-01 bug hunt found exactly that — one aggregate task,
 * `skipped=1` on three of the four targets, and a maintainer entitled to believe all four surfaces
 * had been fuzzed for five minutes each.
 *
 * So the run is one task per target, and this is the reader that refuses to call it coverage unless
 * the reports say so. Silence must not look like coverage.
 */
object FuzzReportSupport {

    /**
     * The display name Jazzer gives the one invocation that actually fuzzes
     * (`FuzzingArgumentsProvider`); every other invocation in a report is a seed-corpus replay.
     */
    const val FUZZING_INVOCATION = "Fuzzing..."

    /** `tests="105" skipped="0" failures="0"` — read as attributes, not as a parsed document. */
    private val SKIPPED = Regex("""<testsuite\b[^>]*\bskipped="(\d+)"""")
    private val TESTS = Regex("""<testsuite\b[^>]*\btests="(\d+)"""")
    private val TIME = Regex("""<testsuite\b[^>]*\btime="([\d.]+)"""")

    /** One target's verdict: what its report proves, in the words the aggregate prints. */
    data class TargetReport(
        val target: String,
        val runs: Int,
        val seconds: Double,
        val skipped: Int,
        val fuzzed: Boolean,
        val problem: String?,
    )

    /** Reads one target's JUnit XML out of [resultsDir] and says whether it fuzzed. */
    fun read(target: String, testClass: String, resultsDir: File): TargetReport {
        val xml = File(resultsDir, "TEST-$testClass.xml")
        if (!xml.isFile) {
            return TargetReport(
                target, 0, 0.0, 0, false,
                "no report at ${xml.path} — the target did not run at all",
            )
        }
        val text = xml.readText()
        val skipped = SKIPPED.find(text)?.groupValues?.get(1)?.toInt() ?: 0
        val runs = TESTS.find(text)?.groupValues?.get(1)?.toInt() ?: 0
        val seconds = TIME.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val fuzzed = text.contains("name=\"$FUZZING_INVOCATION\"")
        val problem = when {
            skipped != 0 ->
                "skipped=$skipped — Jazzer abandoned this target " +
                    "(only one @FuzzTest fuzzes per JVM, so it needs its own task)"
            !fuzzed ->
                "no \"$FUZZING_INVOCATION\" invocation — the seed corpus was replayed but " +
                    "nothing was fuzzed (is JAZZER_FUZZ set?)"
            else -> null
        }
        return TargetReport(target, runs, seconds, skipped, fuzzed, problem)
    }

    /** The per-target line the aggregate prints, whether or not the target passed. */
    fun line(report: TargetReport): String {
        val budget = "%.0fs".format(report.seconds)
        return if (report.problem == null) {
            "  ${report.target}: fuzzed, ${report.runs} invocations in $budget"
        } else {
            "  ${report.target}: NOT FUZZED — ${report.problem}"
        }
    }

    /** Every target that cannot prove it was fuzzed. Empty means the run was what it claimed. */
    fun problems(reports: List<TargetReport>): List<String> =
        reports.filter { it.problem != null }.map { "${it.target}: ${it.problem}" }
}
