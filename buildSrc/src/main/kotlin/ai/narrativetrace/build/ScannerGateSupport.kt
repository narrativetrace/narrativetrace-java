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
 * INTENT: Makes a security-scanner task's outcome honest about whether the scanner ran.
 *
 * An adversarial audit ran `gitleaksScan` and `semgrepScan` in a container
 * without the binaries and got two green builds — the exact failure class that bit the .NET first
 * release, where a secrets scanner had gracefully skipped for the project's entire life (release
 * retrospective rule 2: a graceful-skip tool must prove it has ever run). Graceful degradation for
 * developer ergonomics stays, but it can no longer look like a clean scan:
 *
 * - absence of the binary is a **WARN** plus a recorded `skipped` status — never a silent green;
 * - where security assurance is the point (CI, or `-Pnarrativetrace.security.required=true`),
 *   absence **fails** the task;
 * - every scan task records `ran-clean` / `skipped: …` under `build/reports/security-scans/`, so
 *   "ran clean" and "never ran" are distinguishable after the fact, by humans and by jobs alike.
 */
object ScannerGateSupport {

    /** What a scan task must do when its binary is absent: fail, or warn with [message]. */
    data class MissingBinaryDecision(val fail: Boolean, val message: String)

    /** The decision for a missing [tool] binary, given whether scanners are [required] here. */
    fun onMissingBinary(tool: String, required: Boolean, installHint: String): MissingBinaryDecision =
        if (required) {
            MissingBinaryDecision(
                fail = true,
                message = "$tool is not on PATH and security scanners are required in this " +
                    "context (CI or -Pnarrativetrace.security.required=true). " +
                    "Install: $installHint",
            )
        } else {
            MissingBinaryDecision(
                fail = false,
                message = "$tool not found on PATH — scan SKIPPED. A skipped scan is NOT a clean " +
                    "scan: nothing was checked. Install: $installHint",
            )
        }

    /**
     * What a Semgrep exit code means once `semgrep scan --error` is the invocation: findings and
     * scanner failures are two different failures, and neither may look like the other — a scan
     * that could not run is not "no findings", and findings are not "the scanner broke".
     */
    enum class ScanVerdict { CLEAN, FINDINGS, SCANNER_ERROR }

    /**
     * Classifies a `semgrep scan --error` exit code. Semgrep's own contract: `0` = ran, no
     * (blocking) findings; `1` = findings, only ever with `--error` (without the flag a scan with
     * findings exits `0`, which is exactly the gap this classifier closes); every other code is
     * the scanner failing (`2` fatal, `3` invalid config, `4` invalid pattern, `7` bad token,
     * `8` scan error, …) — see the Semgrep CLI reference. Unknown codes are scanner errors too,
     * never findings and never clean.
     */
    fun semgrepVerdict(exitCode: Int): ScanVerdict =
        when (exitCode) {
            0 -> ScanVerdict.CLEAN
            1 -> ScanVerdict.FINDINGS
            else -> ScanVerdict.SCANNER_ERROR
        }

    /**
     * One line per finding in a Semgrep `--json` report — `<check_id> — <path>:<line>` — so the
     * task's failure message names what fired without the reader opening the JSON. A report that
     * cannot be read (or has no `results` array at all) summarises to a single line saying so:
     * the task is already failing on the exit code, and a parse problem must not turn that into
     * an empty, clean-looking summary.
     */
    @Suppress("UNCHECKED_CAST")
    fun summarizeSemgrepFindings(reportJson: String): List<String> {
        val results = try {
            (JsonSlurper().parseText(reportJson) as? Map<String, Any?>)?.get("results") as? List<Map<String, Any?>>
        } catch (e: Exception) {
            null
        } ?: return listOf("(the Semgrep JSON report could not be parsed — see the scanner output above)")
        return results.map { result ->
            val line = (result["start"] as? Map<String, Any?>)?.get("line")
            "${result["check_id"]} — ${result["path"]}:$line"
        }
    }

    /** Records that [tool]'s scan was skipped for [reason]; readable back via [status]. */
    fun recordSkipped(reportsDir: File, tool: String, reason: String) {
        write(reportsDir, tool, "skipped: $reason")
    }

    /** Records that [tool] actually ran and reported nothing; readable back via [status]. */
    fun recordRanClean(reportsDir: File, tool: String) {
        write(reportsDir, tool, "ran-clean")
    }

    /**
     * The recorded outcome of [tool]'s most recent scan: `ran-clean`, `skipped: …`, or
     * `never-ran` when no scan task has executed at all — three states, so silence cannot
     * masquerade as coverage.
     */
    fun status(reportsDir: File, tool: String): String {
        val file = statusFile(reportsDir, tool)
        return if (file.isFile) file.readText().trim() else "never-ran"
    }

    private fun write(reportsDir: File, tool: String, status: String) {
        reportsDir.mkdirs()
        statusFile(reportsDir, tool).writeText(status + "\n")
    }

    private fun statusFile(reportsDir: File, tool: String) = File(reportsDir, "$tool.status")
}
