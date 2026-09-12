/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.util.Properties

/** The committed `config/duplication/baseline.properties` — main tree only; tests never gate. */
data class DuplicationBaseline(
    val mainPercent: Double,
    val mainLargestCluster: Int,
    val recorded: String,
    val commit: String
)

/** One `config/duplication/exemptions.txt` entry: a deliberate pair, with the reason it exists. */
data class DuplicationExemption(val globA: String, val globB: String, val reason: String)

data class DuplicationCheckResult(val passed: Boolean, val message: String)

/**
 * INTENT: Backs the root `duplicationCheck` task — a ratchet against a committed baseline, not a
 * fixed percentage (see documentation/duplication.md for why: the "right" number depends on the
 * token floor and on how much test scaffolding legitimately repeats, so a fixed threshold is either
 * loose enough to never fire or tight enough to block unrelated work).
 *
 * Only the main tree gates; test-tree duplication is reported by `duplicationReport` and never
 * reaches this class.
 */
object DuplicationCheckSupport {

    /** Percentage-point slack absorbing token-count noise between runs (owner ruling 2026-09-12). */
    const val PERCENT_TOLERANCE = 0.3

    fun readBaseline(file: File): DuplicationBaseline {
        if (!file.isFile) {
            throw IllegalArgumentException(
                "${file.path}: no duplication baseline — run duplicationReport and commit one"
            )
        }
        val props = Properties()
        file.inputStream().use { props.load(it) }
        fun required(key: String): String =
            props.getProperty(key) ?: throw IllegalArgumentException("${file.path}: missing '$key'")
        return DuplicationBaseline(
            mainPercent = required("main.percent").toDouble(),
            mainLargestCluster = required("main.largestCluster").toInt(),
            recorded = props.getProperty("recorded").orEmpty(),
            commit = props.getProperty("commit").orEmpty()
        )
    }

    /**
     * Parses `exemptions.txt`: blank-line-separated entries, each a `# reason` line (one or more,
     * concatenated) immediately followed by one `globA :: globB` pair line. Default-deny: a pair
     * line with no reason above it is a malformed file, not a silent pass — the file, and its
     * intent, is documented in documentation/duplication.md.
     */
    fun readExemptions(file: File): List<DuplicationExemption> {
        if (!file.isFile) return emptyList()
        val exemptions = mutableListOf<DuplicationExemption>()
        var pendingReason: String? = null
        file.readLines().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> pendingReason = null
                line.startsWith("#") -> {
                    val text = line.removePrefix("#").trim()
                    pendingReason = pendingReason?.let { "$it $text" } ?: text
                }
                else -> {
                    val reason = pendingReason ?: throw IllegalArgumentException(
                        "${file.path}:${index + 1}: exemption pair has no '# reason' line above it: $line"
                    )
                    val parts = line.split("::").map { it.trim() }
                    if (parts.size != 2 || parts.any { it.isEmpty() }) {
                        throw IllegalArgumentException(
                            "${file.path}:${index + 1}: expected 'globA :: globB', got: $line"
                        )
                    }
                    exemptions.add(DuplicationExemption(parts[0], parts[1], reason))
                    pendingReason = null
                }
            }
        }
        return exemptions
    }

    /** A cluster is exempt when every occurrence's path matches one of a pair's two globs. */
    fun isExempt(cluster: DuplicationCluster, exemptions: List<DuplicationExemption>): Boolean =
        exemptions.any { exemption ->
            cluster.occurrences.all { occurrence ->
                matchesGlob(exemption.globA, occurrence.file) || matchesGlob(exemption.globB, occurrence.file)
            }
        }

    private fun matchesGlob(glob: String, path: String): Boolean =
        FileSystems.getDefault().getPathMatcher("glob:$glob").matches(Paths.get(path))

    /**
     * The ratchet: fails when main's percentage rose past [PERCENT_TOLERANCE] over the baseline, or
     * when a non-exempt cluster is bigger than the baseline's recorded largest — either one, on its
     * own, is new duplication the baseline never accounted for.
     */
    fun decide(main: DuplicationTreeResult, baseline: DuplicationBaseline, exemptions: List<DuplicationExemption>):
        DuplicationCheckResult {
        val percentFailed = main.percent - baseline.mainPercent > PERCENT_TOLERANCE
        val offending = main.clusters.filter { it.tokens > baseline.mainLargestCluster && !isExempt(it, exemptions) }

        if (!percentFailed && offending.isEmpty()) {
            // The largest-cluster ratchet is anchored on non-exempt clusters: a data table exempted by
            // path must not set the bar a real copy elsewhere is measured against.
            val largest = main.clusters.filter { !isExempt(it, exemptions) }.maxOfOrNull { it.tokens } ?: 0
            return DuplicationCheckResult(
                true,
                "duplicationCheck: main %.1f%% within baseline %.1f%% (+/-%.1f), largest non-exempt cluster %d tokens (baseline %d)"
                    .format(main.percent, baseline.mainPercent, PERCENT_TOLERANCE, largest, baseline.mainLargestCluster)
            )
        }

        val problems = mutableListOf<String>()
        if (percentFailed) {
            problems.add(
                "main duplication rose to %.1f%% (baseline %.1f%% + %.1f tolerance)"
                    .format(main.percent, baseline.mainPercent, PERCENT_TOLERANCE)
            )
        }
        offending.forEach { cluster ->
            val locations = cluster.occurrences.joinToString(" ↔ ") { "${it.file}:${it.startLine}" }
            problems.add(
                "new cluster ${cluster.tokens} tokens (baseline largest ${baseline.mainLargestCluster}): $locations"
            )
        }
        val message = "duplicationCheck failed:\n  " + problems.joinToString("\n  ") + "\n" +
            "Lower the baseline (config/duplication/baseline.properties) with the commit that removes " +
            "the duplication, or add a reasoned 'globA :: globB' pair to config/duplication/exemptions.txt " +
            "if it is deliberate — see documentation/duplication.md."
        return DuplicationCheckResult(false, message)
    }
}
