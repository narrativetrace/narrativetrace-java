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
