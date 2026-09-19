/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class TreeWritesGuardSupportTest {

    @TempDir
    lateinit var dir: File

    private val clean = emptyList<String>()

    // --------------------------------------------------------------- what the tasks wrote

    @Test
    fun `sees nothing when the tasks left the tree as they found it`() {
        assertEquals(clean, TreeWritesGuardSupport.newWorkingTreeEntries(clean, clean))
    }

    @Test
    fun `reports a file a task created`() {
        assertEquals(
            listOf("?? documentation/page.md"),
            TreeWritesGuardSupport.newWorkingTreeEntries(clean, listOf("?? documentation/page.md")),
        )
    }

    @Test
    fun `reports a tracked file a task rewrote`() {
        assertEquals(
            listOf(" M documentation/guide.md"),
            TreeWritesGuardSupport.newWorkingTreeEntries(clean, listOf(" M documentation/guide.md")),
        )
    }

    @Test
    fun `ignores edits that were already there when the tasks started`() {
        // The gate runs on whatever tree the developer has; only what the tasks themselves added
        // is their doing.
        assertEquals(
            clean,
            TreeWritesGuardSupport.newWorkingTreeEntries(
                listOf(" M documentation/guide.md"),
                listOf(" M documentation/guide.md"),
            ),
        )
    }

    @Test
    fun `reports a file whose state changed under a task that did not create it`() {
        assertEquals(
            listOf(" M documentation/guide.md"),
            TreeWritesGuardSupport.newWorkingTreeEntries(
                listOf("?? documentation/guide.md"),
                listOf(" M documentation/guide.md"),
            ),
        )
    }

    @Test
    fun `reports a rename, whose porcelain line names both paths`() {
        assertEquals(
            listOf("R  a.md -> b.md"),
            TreeWritesGuardSupport.newWorkingTreeEntries(clean, listOf("R  a.md -> b.md")),
        )
    }

    // ------------------------------------------------------------------------- the report

    @Test
    fun `the report names what ran and every path it wrote`() {
        val report = TreeWritesGuardSupport
            .reportLines("the verification tasks", listOf(" M documentation/guide.md", "?? out.txt"))
            .joinToString("\n")

        assertTrue(report.contains("the verification tasks"))
        assertTrue(report.contains("documentation/guide.md"))
        assertTrue(report.contains("out.txt"))
        assertTrue(report.contains("2 path(s)"))
    }

    @Test
    fun `the report says what the failure means, so the fix is not 'commit the diff'`() {
        val report = TreeWritesGuardSupport.reportLines("x", listOf("?? a")).joinToString("\n")

        assertTrue(report.contains("without writing"))
        assertTrue(report.contains("temp directory"))
    }

    // ---------------------------------------------------------------------- reading status

    @Test
    fun `reads the porcelain status of a real repository`() {
        initRepository()
        File(dir, "tracked.txt").writeText("one\n")
        git("add", "tracked.txt")
        git("commit", "-m", "first")
        assertEquals(clean, TreeWritesGuardSupport.workingTreeStatus(dir))

        File(dir, "tracked.txt").writeText("two\n")
        File(dir, "stray.txt").writeText("left behind\n")

        assertEquals(
            listOf(" M tracked.txt", "?? stray.txt"),
            TreeWritesGuardSupport.workingTreeStatus(dir).sorted(),
        )
    }

    @Test
    fun `a git that cannot answer is a failure, never a silent pass`() {
        // No repository here at all: the guard must refuse rather than report a clean tree.
        val failure = assertThrows(IllegalStateException::class.java) {
            TreeWritesGuardSupport.workingTreeStatus(dir)
        }

        assertTrue(failure.message!!.contains("cannot answer"))
    }

    private fun initRepository() {
        git("init", "--quiet")
        git("config", "user.email", "build@example.invalid")
        git("config", "user.name", "Build")
        git("config", "commit.gpgsign", "false")
    }

    private fun git(vararg args: String) {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(dir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed: $output" }
    }
}
