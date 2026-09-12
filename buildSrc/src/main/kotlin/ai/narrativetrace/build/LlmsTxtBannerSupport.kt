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
 * INTENT: locates and rewrites the one-line "docs vs published" banner [PublishedVersionSupport]
 * generates, inside the marker pair `<!-- docs-vs-published -->` / `<!-- /docs-vs-published -->`
 * placed directly under `llms.txt`'s H1 (docs-vs-published-gate design note, part (a)). Explicit
 * sentinel comments — the same idiom [SnippetSupport] uses for embedded code blocks — make the
 * line's exact boundary unambiguous, rather than "whichever italic line happens to sit right after
 * the heading."
 */
object LlmsTxtBannerSupport {

    private const val OPEN = "<!-- docs-vs-published -->"
    private const val CLOSE = "<!-- /docs-vs-published -->"

    /** The banner line currently inside the markers, or null when the markers are missing. */
    fun currentLine(file: File): String? {
        if (!file.isFile) {
            return null
        }
        val text = file.readText()
        val start = text.indexOf(OPEN).takeIf { it >= 0 } ?: return null
        val end = text.indexOf(CLOSE, start).takeIf { it >= 0 } ?: return null
        return text.substring(start + OPEN.length, end).trim()
    }

    /**
     * Rewrites the banner to [line] in [file], inserting the marker pair right after the first `#`
     * heading when it is not present yet. Idempotent — re-running with the same [line] leaves the
     * file byte-identical, so `snippetSync` never touches `llms.txt` on an already-current run.
     */
    fun writeLine(file: File, line: String) {
        val text = file.readText()
        val start = text.indexOf(OPEN)
        val rewritten =
            if (start >= 0) {
                val end = text.indexOf(CLOSE, start)
                require(end >= 0) { "${file.path}: $OPEN with no matching $CLOSE" }
                text.substring(0, start) + OPEN + "\n" + line + "\n" + text.substring(end)
            } else {
                insertAfterHeading(text, line)
            }
        if (rewritten != text) {
            file.writeText(rewritten)
        }
    }

    private fun insertAfterHeading(text: String, line: String): String {
        val lines = text.lines().toMutableList()
        val headingIndex = lines.indexOfFirst { it.startsWith("# ") }
        require(headingIndex >= 0) { "no H1 heading to place the docs-vs-published banner under" }
        lines.addAll(headingIndex + 1, listOf("", OPEN, line, CLOSE))
        return lines.joinToString("\n")
    }
}
