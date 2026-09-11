/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.time.Instant

/**
 * The fixed category vocabulary every runtime's `verifyAll`/equivalent reports against (pro repo
 * TODO §35E). A category absent from a given runtime is still reported — as [VerificationStatus.NOT_IMPLEMENTED]
 * — never simply omitted, which is what lets the report replace a hand-maintained matrix.
 */
enum class VerificationCategory(val id: String) {
    UNIT_TESTS("unit-tests"),
    COVERAGE("coverage"),
    MUTATION("mutation"),
    PROPERTY("property"),
    FUZZ_TIER_A("fuzz-tier-a"),
    FUZZ_TIER_B("fuzz-tier-b"),
    BENCHMARKS("benchmarks"),
    ALLOCATION("allocation"),
    ARCHITECTURE("architecture"),
    STRESS_SHORT("stress-short"),
    STRESS_LONG("stress-long"),
    CONFORMANCE("conformance"),
    SECRETS("secrets"),
    SAST("sast"),
    SCA("sca"),
    LINT("lint"),
    FORMAT("format"),
    TYPES("types"),
    COMPLEXITY("complexity"),
    TRANSLATION("translation"),
    CLARITY("clarity"),
}

enum class VerificationStatus(val id: String) {
    PASSED("passed"),
    FAILED("failed"),
    SKIPPED("skipped"),
    NOT_IMPLEMENTED("not-implemented"),
}

/** One row of the report — one category, exactly the fields the SCHEMA (see `reports/verification/SCHEMA.md`) commits to. */
data class CategoryResult(
    val category: VerificationCategory,
    val tool: String,
    val status: VerificationStatus,
    val metrics: Map<String, Any?>,
    val durationSeconds: Double,
    val note: String?,
)

/** The top-level object wrapping every category row. */
data class VerificationRun(
    val runtime: String,
    val version: String,
    val commit: String,
    val host: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val categories: List<CategoryResult>,
) {
    /** `failed` if any row failed; a `not-implemented`/`skipped` row never taints the overall verdict. */
    val overallStatus: String
        get() = if (categories.any { it.status == VerificationStatus.FAILED }) "failed" else "passed"
}

/**
 * INTENT: The single writer AND reader of `reports/verification/<date>.json` — the cross-port
 * contract (pro repo TODO §35E). `renderMarkdown` deliberately reads the JSON back rather than
 * taking the in-memory [VerificationRun] the caller already has, so the Markdown table is provably
 * a rendering of the committed JSON, never a second, independently computed account of the same run.
 */
object VerificationReportSupport {

    fun writeJson(run: VerificationRun, file: File) {
        val root = linkedMapOf<String, Any?>(
            "runtime" to run.runtime,
            "version" to run.version,
            "commit" to run.commit,
            "host" to run.host,
            "started_at" to run.startedAt.toString(),
            "ended_at" to run.endedAt.toString(),
            "overall_status" to run.overallStatus,
            "categories" to run.categories.map { row ->
                linkedMapOf(
                    "category" to row.category.id,
                    "tool" to row.tool,
                    "status" to row.status.id,
                    "metrics" to row.metrics,
                    "duration_seconds" to row.durationSeconds,
                    "note" to row.note,
                )
            },
        )
        file.parentFile?.mkdirs()
        file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(root)))
    }

    @Suppress("UNCHECKED_CAST")
    fun readJson(file: File): Map<String, Any?> = JsonSlurper().parseText(file.readText()) as Map<String, Any?>

    /** Renders the human table straight from [jsonFile] — see the class doc for why it re-reads rather than reusing memory. */
    @Suppress("UNCHECKED_CAST")
    fun renderMarkdown(jsonFile: File): String {
        val root = readJson(jsonFile)
        val categories = root["categories"] as List<Map<String, Any?>>
        val sb = StringBuilder()

        sb.appendLine("# Verification run — ${root["runtime"]} ${root["version"]}")
        sb.appendLine()
        sb.appendLine("- commit: `${root["commit"]}`")
        sb.appendLine("- host: ${root["host"]}")
        sb.appendLine("- started: ${root["started_at"]}")
        sb.appendLine("- ended: ${root["ended_at"]}")
        sb.appendLine("- **overall status: ${root["overall_status"]}**")
        sb.appendLine()
        sb.appendLine("| Category | Tool | Status | Duration (s) | Metrics | Note |")
        sb.appendLine("|---|---|---|---|---|---|")
        categories.forEach { row ->
            val metrics = row["metrics"] as? Map<String, Any?> ?: emptyMap()
            val metricsCell = if (metrics.isEmpty()) "—" else metrics.entries.joinToString("; ") { (k, v) -> "$k=$v" }
            val note = (row["note"] as? String)?.takeIf { it.isNotBlank() } ?: ""
            // JsonSlurper hands back a BigDecimal (or Integer/Long) for a JSON number, never a
            // Double — `as? Double` silently misses every one of them and prints 0.0 for every
            // row (found running this against a real report). `Number` covers all of them.
            val durationSeconds = (row["duration_seconds"] as? Number)?.toDouble() ?: 0.0
            sb.appendLine(
                "| ${row["category"]} | ${row["tool"]} | ${statusBadge(row["status"] as String)} | " +
                    "%.1f".format(durationSeconds) + " | $metricsCell | $note |"
            )
        }
        return sb.toString()
    }

    private fun statusBadge(status: String): String = when (status) {
        "passed" -> "passed"
        "failed" -> "**FAILED**"
        "skipped" -> "skipped"
        "not-implemented" -> "not-implemented"
        else -> status
    }
}
