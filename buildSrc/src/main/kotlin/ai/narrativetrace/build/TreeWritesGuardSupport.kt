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
 * INTENT: A verification task may read the repository it runs in; it may never write to it.
 *
 * The failure class this closes, found in a sibling runtime: a test asked "is a docs sync pending?"
 * by calling the function that PERFORMS the sync, so the suite rewrote tracked pages while claiming
 * to check them. A suite that writes where the repository lives is not a test, it is an unreviewed
 * edit — and the question a check asks must be answerable without writing. The `check`/`sync` pairs
 * in this build (`snippetCheck`/`snippetSync`, the skills renderer's drift test, the translation
 * headers) already split that way; this keeps the split honest for every test and every tool a test
 * reaches, including the ones nobody has written yet.
 *
 * The guard compares `git status --porcelain` before and after the verification tasks and fails on
 * anything new. Two consequences, both deliberate:
 *
 * - it runs on whatever tree the developer has — only what the tasks themselves added is theirs;
 * - a task IS allowed to write its own `build/` directory, which is git-ignored and therefore never
 *   appears in the porcelain output at all; it is not allowed to touch anything the repo tracks.
 *
 * Never skips: `git` that cannot answer is a failure, not a pass (release rule 2 — a tool that
 * gracefully skips must be able to prove it ever ran).
 */
object TreeWritesGuardSupport {

    /**
     * The `git status --porcelain` lines [after] has that [before] did not — the working-tree
     * changes the tasks in between are responsible for. Whole lines, so a path whose status changed
     * (untracked becoming modified, say) still reads as new. Pure.
     */
    fun newWorkingTreeEntries(before: List<String>, after: List<String>): List<String> {
        val seen = before.toSet()
        return after.filter { it !in seen }
    }

    /** The failure report: what ran, what it wrote, and what to do about it. */
    fun reportLines(what: String, entries: List<String>): List<String> =
        listOf("treeWritesCheck: $what changed ${entries.size} path(s) in the working tree:") +
            entries.map { "  $it" } +
            listOf(
                "A test must answer its question without writing where the repository lives.",
                "Make the 'ask' half read-only, or take the output root as a parameter and point",
                "it at a temp directory.",
            )

    /** `git status --porcelain` as lines. Throws rather than skipping when git cannot answer. */
    fun workingTreeStatus(repoRoot: File): List<String> {
        val process = ProcessBuilder("git", "status", "--porcelain")
            .directory(repoRoot)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw IllegalStateException(
                "treeWritesCheck: `git status --porcelain` exited $exitCode in $repoRoot — the " +
                    "guard cannot answer whether the tree was written to:\n$output"
            )
        }
        return output.lines().filter { it.isNotEmpty() }
    }
}
