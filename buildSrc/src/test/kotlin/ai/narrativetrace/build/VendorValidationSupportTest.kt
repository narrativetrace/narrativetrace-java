/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class VendorValidationSupportTest {

    @Test
    fun `the registry carries one row today, for the marketplace file`() {
        val rows = VendorValidationSupport.CHECKS

        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals("claude", row.tool)
        assertEquals(listOf("plugin", "validate"), row.validate)
        assertEquals(".claude-plugin/marketplace.json", row.artifact)
        assertEquals(
            listOf(".claude-plugin", ".claude/skills"),
            row.stagedPaths,
            "the marketplace root is the artifact AND the plugin it points at",
        )
    }

    // --- the skip / fail / pass decision, with a fake tool on the PATH --------------------

    @TempDir
    lateinit var bin: File

    @TempDir
    lateinit var stage: File

    private val row = VendorCheck(
        tool = "faketool",
        probe = listOf("--version"),
        validate = listOf("validate"),
        artifact = "some/artifact.json",
        stagedPaths = listOf("some"),
        installHint = "put faketool on the PATH",
    )

    /**
     * A fake vendor CLI: answers its probe (`--version`) cleanly, then exits [exitCode] printing
     * [output] for the validation itself — the shape a real CLI has.
     */
    private fun fakeTool(exitCode: Int, output: String = "") {
        writeFakeTool(
            """
            #!/bin/sh
            case "$1" in --version) echo "faketool 1.0"; exit 0 ;; esac
            echo '$output'
            exit $exitCode
            """.trimIndent()
        )
    }

    private fun writeFakeTool(script: String) {
        val file = File(bin, row.tool)
        file.writeText(script + "\n")
        file.setExecutable(true)
    }

    @Test
    fun `an absent tool skips, naming the tool and how to install it`() {
        val result = VendorValidationSupport.validate(row, stage, bin.path)

        assertEquals(VendorOutcome.SKIPPED, result.outcome)
        assertTrue(result.message.contains("faketool"), result.message)
        assertTrue(result.message.contains("put faketool on the PATH"), result.message)
    }

    @Test
    fun `a validator that exits zero passes`() {
        fakeTool(exitCode = 0, output = "Validation passed")

        val result = VendorValidationSupport.validate(row, stage, bin.path)

        assertEquals(VendorOutcome.PASSED, result.outcome)
        assertTrue(result.output.contains("Validation passed"), result.output)
    }

    @Test
    fun `a tool that is present but fails its own probe skips, not fails`() {
        writeFakeTool("#!/bin/sh\nexit 7")

        val result = VendorValidationSupport.validate(row, stage, bin.path)

        assertEquals(VendorOutcome.SKIPPED, result.outcome)
        assertTrue(result.message.contains("probe failed"), result.message)
        assertTrue(result.message.contains("put faketool on the PATH"), result.message)
    }

    @Test
    fun `a validator that exits non-zero fails, keeping what it printed`() {
        fakeTool(exitCode = 1, output = "owner: Invalid input")

        val result = VendorValidationSupport.validate(row, stage, bin.path)

        assertEquals(VendorOutcome.FAILED, result.outcome)
        assertTrue(result.output.contains("owner: Invalid input"), result.output)
        assertTrue(result.message.contains("some/artifact.json"), result.message)
    }

    // --- staging what a publish would ship ------------------------------------------------

    private fun git(dir: File, vararg args: String) {
        val process = ProcessBuilder(
            listOf("git", "-c", "user.email=t@example.com", "-c", "user.name=T") + args.toList()
        ).directory(dir).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), "git ${args.joinToString(" ")}: $output")
    }

    @Test
    fun `staging takes the committed tree, never the working tree`(@TempDir repo: File, @TempDir into: File) {
        git(repo, "init", "--quiet")
        File(repo, ".claude-plugin").mkdirs()
        File(repo, ".claude-plugin/marketplace.json").writeText("committed")
        File(repo, "untracked.txt").writeText("never staged")
        git(repo, "add", ".claude-plugin")
        git(repo, "commit", "--quiet", "-m", "m")
        File(repo, ".claude-plugin/marketplace.json").writeText("edited after the commit")

        VendorValidationSupport.stageFromHead(repo, listOf(".claude-plugin"), into)

        assertEquals("committed", File(into, ".claude-plugin/marketplace.json").readText())
        assertTrue(!File(into, "untracked.txt").exists(), "an untracked file must not be staged")
    }

    // --- the run's own verdict, and the record verifyAll reads back ------------------------

    private fun result(outcome: VendorOutcome, tool: String = "faketool") =
        VendorCheckResult(tool, "a/b.json", outcome, "message", "output")

    @Test
    fun `one failing row fails the whole run`() {
        val results = listOf(result(VendorOutcome.PASSED), result(VendorOutcome.FAILED))

        assertEquals(VendorOutcome.FAILED, VendorValidationSupport.aggregate(results))
    }

    @Test
    fun `a run where one row passed and the others skipped passed`() {
        val results = listOf(result(VendorOutcome.SKIPPED), result(VendorOutcome.PASSED))

        assertEquals(VendorOutcome.PASSED, VendorValidationSupport.aggregate(results))
    }

    @Test
    fun `a run where every row skipped is a skip, never a pass`() {
        assertEquals(
            VendorOutcome.SKIPPED,
            VendorValidationSupport.aggregate(listOf(result(VendorOutcome.SKIPPED))),
        )
    }

    @Test
    fun `no rows at all is a skip`() {
        assertEquals(VendorOutcome.SKIPPED, VendorValidationSupport.aggregate(emptyList()))
    }

    @Test
    fun `a recorded row reads back, and an unrecorded tool reads back as never-ran`(@TempDir reports: File) {
        VendorValidationSupport.record(reports, result(VendorOutcome.SKIPPED))

        assertTrue(
            VendorValidationSupport.recordedStatus(reports, "faketool").startsWith("skipped: "),
            VendorValidationSupport.recordedStatus(reports, "faketool"),
        )
        assertEquals("never-ran", VendorValidationSupport.recordedStatus(reports, "othertool"))
    }
}
