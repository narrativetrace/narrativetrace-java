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
 * INTENT: Backs the root `commentHygiene` task — a per-commit lint against audit history and
 * port-framing left behind in code comments, mirroring the TS repo's `tools/comment-hygiene.ts`
 * (read-only reference, not shared code) for the Java source tree. A code comment carries the
 * constraint a reader or an agent must respect, not the ledger entry that produced it — that
 * history belongs in the commit that made the change, never in the comment that survives it.
 */
object CommentHygieneSupport {

    /** An explicit governance citation, a bare audit/ruling date in parens, or shipped-release
     * wording, as it would read INSIDE a source comment — same shape as the TS repo's
     * `HISTORY_PATTERN`. */
    val HISTORY_PATTERN = Regex("""owner ruling|ruled 20\d\d|\(20\d\d-\d\d-\d\d\)|, unreleased\)""")

    /** Java is the master source in this family — a code comment naming a peer runtime as the
     * thing this code follows or mirrors belongs in the parity docs, not in source: it reads as
     * stale the day the peer catches up, and it inverts which runtime is authoritative. */
    val PORT_FRAMING_PATTERN = Regex(
        """golden source|the (TS|TypeScript|Python|\.NET|dotnet|Swift) (port|runtime)'s|""" +
            """mirrors (TS|Python|\.NET)|as in (TS|Python|\.NET)|ported to"""
    )

    /** The house JavaDoc tags (code-design-brief.md) and `INTENT:` lines are never flagged, even
     * when the same line also carries a date or a peer-runtime mention — they are exactly the
     * rule-first, AI-consumable content this lint exists to protect. */
    private val EXEMPT_PATTERN = Regex("""@llmNote|@sideEffects|@pattern|INTENT:""")

    /** One lint hit: repo-relative POSIX path, 1-indexed line, trimmed text, and which pattern
     * fired ("history" or "port-framing") — kept distinct so a violation message can say which
     * rule the line broke. */
    data class Hit(val file: String, val line: Int, val text: String, val rule: String)

    data class LintResult(val violations: List<Hit>, val staleAllowlistEntries: List<String>)

    /** Every `.java` file under each `<moduleName>/src/main/java` in [moduleNames] (walked
     * recursively under [repoRoot]) — mirrors the TS repo's per-package `src` walk, one module
     * directory at a time; a module without that directory (no main sources) contributes
     * nothing. Sorted. */
    fun moduleSourceFiles(repoRoot: File, moduleNames: Collection<String>): List<File> {
        val files = mutableListOf<File>()
        for (name in moduleNames) {
            val src = File(repoRoot, "$name/src/main/java")
            if (src.isDirectory) {
                files += src.walkTopDown().filter { it.isFile && it.extension == "java" }
            }
        }
        return files.sortedBy { it.path }
    }

    /** Every Kotlin file under `buildSrc/src/main/kotlin` — included because walking it costs
     * nothing next to the published modules above; empty when the directory does not exist. */
    fun buildSrcSourceFiles(repoRoot: File): List<File> {
        val src = File(repoRoot, "buildSrc/src/main/kotlin")
        if (!src.isDirectory) return emptyList()
        return src.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.toList()
    }

    /** Every [HISTORY_PATTERN]/[PORT_FRAMING_PATTERN] hit in `content`, skipping any line the
     * house tags/`INTENT:` exempt — 1-indexed, trimmed. */
    private fun linesMatching(content: String): List<Triple<Int, String, String>> {
        val hits = mutableListOf<Triple<Int, String, String>>()
        content.lines().forEachIndexed { index, rawLine ->
            if (EXEMPT_PATTERN.containsMatchIn(rawLine)) return@forEachIndexed
            val rule = when {
                HISTORY_PATTERN.containsMatchIn(rawLine) -> "history"
                PORT_FRAMING_PATTERN.containsMatchIn(rawLine) -> "port-framing"
                else -> null
            } ?: return@forEachIndexed
            hits += Triple(index + 1, rawLine.trim(), rule)
        }
        return hits
    }

    /** Every hit across [files], repo-relative to [repoRoot] (POSIX-separated) and sorted by file
     * then line — allowlist filtering is [lint]'s job, not this function's. */
    fun findHits(repoRoot: File, files: List<File>): List<Hit> {
        val hits = mutableListOf<Hit>()
        for (file in files) {
            val relative = file.relativeTo(repoRoot).path.replace(File.separatorChar, '/')
            for ((line, text, rule) in linesMatching(file.readText())) {
                hits += Hit(relative, line, text, rule)
            }
        }
        return hits
    }

    /** Parses the allowlist JSON — a flat `{"path/to/File.java": "reason"}` object, same shape as
     * the TS repo's `comment-hygiene-allowlist.json`. Missing file reads as the empty allowlist
     * (a fresh clone with nothing excused yet), never a failure. */
    @Suppress("UNCHECKED_CAST")
    fun readAllowlist(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        val text = file.readText().trim()
        if (text.isEmpty() || text == "{}") return emptyMap()
        val parsed = JsonSlurper().parseText(text) as Map<String, Any?>
        return parsed.mapValues { (_, value) -> value.toString() }
    }

    /** Excuses exactly the files named in [allowlist]; an allowlisted file with zero current hits
     * is flagged as stale — the allowlist is meant to shrink as each module's own wave lands,
     * never to accumulate entries nobody has to remove. */
    fun lint(hits: List<Hit>, allowlist: Map<String, String>): LintResult {
        val hitFiles = hits.map { it.file }.toSet()
        return LintResult(
            violations = hits.filter { it.file !in allowlist },
            staleAllowlistEntries = allowlist.keys.filter { it !in hitFiles }.sorted()
        )
    }
}
