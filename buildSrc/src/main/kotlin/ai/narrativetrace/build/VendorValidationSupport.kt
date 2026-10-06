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
 * One vendor's own validator for one artifact this repository publishes, as DATA.
 *
 * @property tool the executable, looked up on `PATH` by this name
 * @property probe argv (after [tool]) that proves the tool answers at all — a tool present but
 *     unusable must skip like an absent one, never fail a gate about our own artifact
 * @property validate argv (after [tool]) that performs the validation; the staged artifact root is
 *     appended as the last argument
 * @property artifact repo-relative path of what this row validates, for the log and the report
 * @property stagedPaths repo-relative paths the validation needs staged — the artifact and whatever
 *     it points at (a marketplace file is only valid together with the plugin directory its entry
 *     names), so the tool sees the same tree a consumer's clone would
 * @property installHint one line telling a reader how to make [tool] available
 */
data class VendorCheck(
    val tool: String,
    val probe: List<String>,
    val validate: List<String>,
    val artifact: String,
    val stagedPaths: List<String>,
    val installHint: String,
)

/** What one row's run amounted to. A skip is never a pass: nothing was validated. */
enum class VendorOutcome { PASSED, FAILED, SKIPPED }

/** One row's result: what ran, what it decided, and everything it printed. */
data class VendorCheckResult(
    val tool: String,
    val artifact: String,
    val outcome: VendorOutcome,
    val message: String,
    val output: String,
)

/**
 * INTENT: Backs the root `vendorValidate` task — the seam where a VENDOR's own validator checks an
 * artifact this repository publishes for that vendor to read.
 *
 * A registry, not one hard-coded invocation: the family publishes registry artifacts for several
 * agent tools, and each new one joins as a ROW (tool, probe, validate, artifact) rather than as new
 * machinery. The task stays in the heavy verification tier and never rides `check`: a per-commit
 * gate may not depend on a third-party CLI being installed.
 *
 * The precondition lives HERE, in the row's own run, never in a CI-side exclusion list: an absent
 * tool SKIPS with one line naming it and how to install it, and a skip is recorded as a skip.
 */
object VendorValidationSupport {

    /**
     * Every vendor validation this repository knows how to run. One row today: the plugin
     * marketplace file, validated by the agent CLI that reads it.
     */
    val CHECKS: List<VendorCheck> = listOf(
        VendorCheck(
            tool = "claude",
            probe = listOf("--version"),
            validate = listOf("plugin", "validate"),
            artifact = ".claude-plugin/marketplace.json",
            stagedPaths = listOf(".claude-plugin", ".claude/skills"),
            installHint = "install the agent CLI (npm i -g @anthropic-ai/claude-code) and re-run",
        ),
    )

    /**
     * Runs one row against [artifactRoot] — the staged tree holding the artifact — resolving the
     * tool on [pathValue] (the `PATH` to search; the process environment's own when null).
     *
     * Three outcomes, in this order: the tool is absent or does not answer its probe → SKIPPED with
     * a one-line reason naming it and [VendorCheck.installHint]; the validator exits zero → PASSED;
     * anything else → FAILED, carrying everything the validator printed.
     */
    fun validate(check: VendorCheck, artifactRoot: File, pathValue: String?): VendorCheckResult {
        val executable = executableOnPath(check.tool, pathValue)
            ?: return skipped(check, "not found on PATH")
        val (probeExit, _) = execute(executable, check.probe, artifactRoot)
        if (probeExit != 0) {
            return skipped(check, "found at $executable but its own probe failed (exit $probeExit)")
        }
        val (exitCode, output) = execute(executable, check.validate + artifactRoot.path, artifactRoot)
        return if (exitCode == 0) passed(check, output) else failed(check, exitCode, output)
    }

    /**
     * The whole run's verdict: FAILED when any row failed, PASSED when at least one row genuinely
     * ran clean, SKIPPED otherwise — including when there are no rows at all. A run where every
     * vendor CLI was absent validated nothing, and must never read as a pass.
     */
    fun aggregate(results: List<VendorCheckResult>): VendorOutcome = when {
        results.any { it.outcome == VendorOutcome.FAILED } -> VendorOutcome.FAILED
        results.any { it.outcome == VendorOutcome.PASSED } -> VendorOutcome.PASSED
        else -> VendorOutcome.SKIPPED
    }

    /**
     * Records one row's outcome under [reportsDir], so "validated" and "never validated" stay
     * distinguishable after the fact — by a person and by `verifyAll`, which reads these back rather
     * than inferring a status from an exit code that a skip also leaves at zero.
     */
    fun record(reportsDir: File, result: VendorCheckResult) {
        reportsDir.mkdirs()
        statusFile(reportsDir, result.tool)
            .writeText(result.outcome.name.lowercase() + ": " + result.message + "\n")
    }

    /** What [tool]'s last recorded run said, or `never-ran` when it has no record at all. */
    fun recordedStatus(reportsDir: File, tool: String): String {
        val file = statusFile(reportsDir, tool)
        return if (file.isFile) file.readText().trim() else "never-ran"
    }

    private fun statusFile(reportsDir: File, tool: String) = File(reportsDir, "$tool.status")

    /**
     * Stages [paths] out of `HEAD` into [into]: `git archive`, never the working tree, so what a
     * vendor validates is what a publish would ship — uncommitted edits are invisible here exactly
     * as they are to the publish script's own staging.
     *
     * @throws IllegalStateException when git or tar cannot produce the tree; a validation gate that
     *     silently validated nothing would be worse than one that stops.
     */
    fun stageFromHead(repoRoot: File, paths: List<String>, into: File) {
        into.mkdirs()
        val archive = File(into, "head.tar")
        runOrThrow(repoRoot, listOf("git", "archive", "--format=tar", "-o", archive.path, "HEAD", "--") + paths)
        runOrThrow(into, listOf("tar", "-xf", archive.path, "-C", into.path))
        archive.delete()
    }

    private fun runOrThrow(directory: File, command: List<String>) {
        val process = ProcessBuilder(command).directory(directory).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        check(exitCode == 0) { "${command.joinToString(" ")} failed (exit $exitCode): $output" }
    }

    /** The first executable named [name] on [pathValue], or null when nothing there can run. */
    fun executableOnPath(name: String, pathValue: String?): File? =
        (pathValue ?: System.getenv("PATH"))
            ?.split(File.pathSeparatorChar)
            ?.map { File(it, name) }
            ?.firstOrNull { it.canExecute() }

    /** PASSED: the vendor ran and accepted the artifact. */
    private fun passed(check: VendorCheck, output: String) = VendorCheckResult(
        check.tool,
        check.artifact,
        VendorOutcome.PASSED,
        "validated ${check.artifact}",
        output,
    )

    /** FAILED: the vendor ran and rejected the artifact — its own words are kept in [output]. */
    private fun failed(check: VendorCheck, exitCode: Int, output: String) = VendorCheckResult(
        check.tool,
        check.artifact,
        VendorOutcome.FAILED,
        "${check.tool} rejected ${check.artifact} (exit $exitCode)",
        output,
    )

    /** SKIPPED, with the one line a reader needs: which tool, why, and how to make it available. */
    private fun skipped(check: VendorCheck, reason: String) = VendorCheckResult(
        check.tool,
        check.artifact,
        VendorOutcome.SKIPPED,
        "${check.tool} $reason — ${check.artifact} was NOT validated; ${check.installHint}",
        "",
    )

    /** Exit code and merged output of one invocation, run with [artifactRoot] as its directory. */
    private fun execute(executable: File, args: List<String>, artifactRoot: File): Pair<Int, String> {
        val process = ProcessBuilder(listOf(executable.path) + args)
            .directory(artifactRoot)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        return process.waitFor() to output
    }
}
