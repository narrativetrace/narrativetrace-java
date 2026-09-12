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
 * INTENT: Backs the root `snippetCheck`/`snippetSync` tasks — rule 8 (docs as tests), layer 1. A
 * quickstart's code and output are embedded from a real project the build compiles, tests and runs,
 * never typed into the page; this is the mechanism that keeps the embedded copy honest, the same
 * way [TranslationCheckSupport] keeps a translated code block honest against its English source.
 *
 * A snippet is a marker pair of HTML comments around a fenced code block:
 *
 * ```markdown
 * <!-- snippet: sixty-seconds/src/main/java/com/example/orders/Main.java -->
 * ```java
 * …the file, verbatim…
 * ```
 * <!-- /snippet -->
 * ```
 *
 * `snippet:` names a path relative to the repository root. `region=NAME` selects a `//
 * snippet:begin NAME` … `// snippet:end NAME` (or the XML/HTML equivalent, `<!-- snippet:begin
 * NAME -->` … `<!-- snippet:end NAME -->`) window inside that file instead of the whole thing.
 * `mask=duration` replaces `— \d+(\.\d+)?ms` with `— Nms` on both sides *only for the comparison*
 * — neither the source file nor the page is ever rewritten with a masked value; the page keeps
 * whatever real duration the last `snippetSync` copied over, same as it always has.
 */
object SnippetSupport {

    data class SnippetMarker(val path: String, val region: String?, val mask: String?)

    private val OPEN_MARKER = Regex("""<!--\s*snippet:\s*(\S+)((?:\s+\S+)*)\s*-->""")
    private val CLOSE_MARKER = Regex("""^<!--\s*/snippet\s*-->$""")
    private val FENCE = Regex("""```\S*""")
    private val OPTION = Regex("""(\w+)=(\S+)""")

    private val DURATION_MASK = Regex("""— \d+(?:\.\d+)?ms""")

    /** Parses one `<!-- snippet: ... -->` line; null when the line is not an opening marker. */
    fun parseMarker(line: String): SnippetMarker? {
        val match = OPEN_MARKER.matchEntire(line.trim()) ?: return null
        val options = OPTION.findAll(match.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
        return SnippetMarker(match.groupValues[1], options["region"], options["mask"])
    }

    /**
     * Every Markdown file this mechanism governs: repository-wide, excluding translated mirrors
     * (any `documentation/<lang>/` directory, `i18n`) and build/hidden directories — the same
     * "English pages only" scope `snippetSync` writes to. Mirrors are never touched here;
     * `translationCheck` is what flags their code blocks going stale against the English source.
     *
     * <p>`documentation/llms.txt` is included too, despite its extension: it is the first thing an
     * agent reads, its "Install and first trace" block is the same install/run/output triple the
     * 60-second page shows, and an untracked copy of that triple would drift from the real one
     * silently — the one file this mechanism must not silently skip over an extension technicality.
     * It carries no translations, so it is never a language-directory concern.
     */
    fun englishMarkdownFiles(repoRoot: File): List<File> {
        val languageDirs =
            repoRoot.resolve("documentation").listFiles().orEmpty()
                .filter { it.isDirectory && it.name != "i18n" }
                .map { it.canonicalFile }
                .toSet()
        val llmsTxt = repoRoot.resolve("documentation/llms.txt").canonicalFile
        return repoRoot.walkTopDown()
            .onEnter { dir -> dir == repoRoot || (dir.name != "build" && !dir.name.startsWith(".")) }
            .onEnter { dir -> dir.canonicalFile !in languageDirs }
            .filter { it.isFile && (it.extension == "md" || it.canonicalFile == llmsTxt) }
            .filter { OPEN_MARKER.containsMatchIn(it.readText()) }
            .toList()
    }

    /** Every problem found across every governed page, sorted — empty when everything is in sync. */
    fun check(repoRoot: File): List<String> =
        englishMarkdownFiles(repoRoot).flatMap { checkFile(repoRoot, it) }.sorted()

    private fun checkFile(repoRoot: File, file: File): List<String> {
        val relative = relativePath(repoRoot, file)
        return blocks(file).mapNotNull { block ->
            val expected =
                try {
                    render(repoRoot, block.marker)
                } catch (e: SnippetException) {
                    return@mapNotNull "$relative: snippet ${block.marker.path} — ${e.message}"
                }
            val (maskedExpected, maskedActual) = mask(block.marker.mask, expected, block.content)
                ?: return@mapNotNull "$relative: snippet ${block.marker.path} — unknown mask " +
                    "'${block.marker.mask}'"
            if (maskedExpected == maskedActual) {
                null
            } else {
                "$relative: fenced block for '${block.marker.path}' differs from source " +
                    "(compare $relative and ${block.marker.path}) — run snippetSync"
            }
        }
    }

    /**
     * Rewrites every drifted fenced block in every governed page to match its source, English pages
     * only (translated mirrors are never touched — see [englishMarkdownFiles]). Returns one line per
     * block actually changed, sorted, empty when nothing needed it.
     */
    fun sync(repoRoot: File): List<String> {
        val changed = mutableListOf<String>()
        for (file in englishMarkdownFiles(repoRoot)) {
            val relative = relativePath(repoRoot, file)
            val original = file.readText()
            var rewritten = original
            for (block in blocks(file)) {
                val expected = render(repoRoot, block.marker)
                if (expected != block.content) {
                    rewritten = rewritten.replaceFirst(block.fullMatch, block.replacement(expected))
                    changed.add("$relative: synced '${block.marker.path}'")
                }
            }
            if (rewritten != original) {
                file.writeText(rewritten)
            }
        }
        return changed.sorted()
    }

    // ---------------------------------------------------------------------------------------
    // Block discovery
    // ---------------------------------------------------------------------------------------

    private class SnippetException(message: String) : RuntimeException(message)

    private class Block(val marker: SnippetMarker, val content: String, val fullMatch: String) {
        /** The whole marker pair with [newContent] substituted for the fenced block's body. */
        fun replacement(newContent: String): String {
            val fenceLine = FENCE.find(fullMatch)!!.value
            val closeFence = fullMatch.lastIndexOf("```")
            val openFenceEnd = fullMatch.indexOf(fenceLine) + fenceLine.length
            val before = fullMatch.substring(0, openFenceEnd)
            val after = fullMatch.substring(closeFence)
            val body = if (newContent.endsWith("\n")) newContent else "$newContent\n"
            return "$before\n$body$after"
        }
    }

    /** Every marker pair in [file], in document order. */
    private fun blocks(file: File): List<Block> {
        val lines = file.readText().lines()
        val blocks = mutableListOf<Block>()
        var i = 0
        while (i < lines.size) {
            val marker = parseMarker(lines[i])
            if (marker == null) {
                i++
                continue
            }
            val openIdx = i + 1 + (lines.drop(i + 1).indexOfFirst { it.isNotBlank() })
            if (openIdx <= i || !FENCE.matches(lines.getOrNull(openIdx)?.trim().orEmpty())) {
                i++
                continue
            }
            val closeIdx = (openIdx + 1 until lines.size).firstOrNull { lines[it].trim() == "```" }
            if (closeIdx == null) {
                i++
                continue
            }
            val markerCloseIdx = (closeIdx + 1 until lines.size).firstOrNull { lines[it].isNotBlank() }
            if (markerCloseIdx == null || !CLOSE_MARKER.matches(lines[markerCloseIdx].trim())) {
                i++
                continue
            }
            val content = lines.subList(openIdx + 1, closeIdx).joinToString("\n").let { if (it.isEmpty()) it else "$it\n" }
            val fullMatch = lines.subList(i, markerCloseIdx + 1).joinToString("\n")
            blocks.add(Block(marker, content, fullMatch))
            i = markerCloseIdx + 1
        }
        return blocks
    }

    // ---------------------------------------------------------------------------------------
    // Source rendering
    // ---------------------------------------------------------------------------------------

    /** The source's current content for [marker] — the whole file, or its named region. */
    private fun render(repoRoot: File, marker: SnippetMarker): String {
        val source = repoRoot.resolve(marker.path)
        if (!source.isFile) {
            throw SnippetException("source '${marker.path}' is missing")
        }
        val text = stripLicenseHeader(source.readText())
        if (marker.region == null) {
            return text
        }
        return region(text, marker.region)
            ?: throw SnippetException("region '${marker.region}' not found in '${marker.path}'")
    }

    private val LICENSE_MARKERS = listOf("SPDX-License-Identifier", "Licensed under")

    /**
     * Strips a leading license-header comment block from the SOURCE side only — never the page —
     * before it is compared or copied. The publish script stamps every source file of the public
     * snapshot with a header (a run of `//` lines, one `/* ... */` block, or — for XML, `logback.xml`
     * included — one `<!-- ... -->` block) the page never showed and the in-tree source never
     * carries, so an un-stripped comparison would fail only in the publish verify, on files that
     * were fine right up to that point.
     *
     * <p>Only a leading comment whose own text names the header ([LICENSE_MARKERS]) is touched, and
     * only that comment plus the blank line right after it, when there is one — never any other
     * leading comment. A source file's own leading comment (this tutorial's `// path/to/File.java`
     * label line, or its `<!-- src/main/resources/logback.xml -->` XML equivalent) never carries
     * that text and is always preserved verbatim; `snippetSync` never writes a header into either
     * side, since it only ever writes to the page.
     */
    private fun stripLicenseHeader(text: String): String {
        val lines = text.lines()
        val first = lines.firstOrNull()?.trimStart().orEmpty()
        val headerEnd =
            when {
                first.startsWith("/*") -> blockCommentEnd(lines, "*/")
                first.startsWith("<!--") -> blockCommentEnd(lines, "-->")
                first.startsWith("//") -> lineCommentEnd(lines)
                else -> null
            } ?: return text
        if (!lines.subList(0, headerEnd).any { line -> LICENSE_MARKERS.any { line.contains(it) } }) {
            return text
        }
        val bodyStart = if (lines.getOrNull(headerEnd)?.isBlank() == true) headerEnd + 1 else headerEnd
        return lines.subList(bodyStart, lines.size).joinToString("\n")
    }

    /** Exclusive end index of a leading block comment closed by [closeToken] (C-style or XML), or null when it never closes. */
    private fun blockCommentEnd(lines: List<String>, closeToken: String): Int? =
        lines.indices.firstOrNull { lines[it].contains(closeToken) }?.let { it + 1 }

    /** A `Copyright (c) 2026 ...` line — the one line of this repo's stamped headers naming neither [LICENSE_MARKERS]. */
    private val COPYRIGHT_LINE = Regex("""Copyright \(c\) \d{4}""")

    /**
     * Exclusive end index of a leading license header within a run of `//` lines. A stamped header
     * carries no closing token the way a block comment's `*` `/` does, so its end is found from the
     * run's last marker-bearing line, extended through any immediately following copyright line —
     * the shape this repo's own publish script actually writes (SPDX/Licensed-under line(s), then
     * one Copyright line, then the file's real first line with no blank line before it). A genuine
     * leading comment the file carries on its own (this tutorial's `// path/to/File.java` label
     * line, say) names neither a marker nor a copyright, so it is never pulled into the header even
     * when — as here — it is `//`-commented too and touches the header with nothing between them.
     *
     * <p>Returns the whole contiguous run when it carries no marker at all, so the caller's own
     * marker check (right after this call) still correctly says "not a header".
     */
    private fun lineCommentEnd(lines: List<String>): Int {
        val run = lines.indices.firstOrNull { !lines[it].trimStart().startsWith("//") } ?: lines.size
        val lastMarker =
            (0 until run).lastOrNull { i -> LICENSE_MARKERS.any { lines[i].contains(it) } } ?: return run
        var end = lastMarker + 1
        while (end < run && COPYRIGHT_LINE.containsMatchIn(lines[end])) {
            end++
        }
        return end
    }

    /**
     * Extracts the window between `// snippet:begin NAME` / `// snippet:end NAME` (or the XML/HTML
     * form, `<!-- snippet:begin NAME -->` / `<!-- snippet:end NAME -->`) — whichever comment style
     * [text] actually uses — excluding the marker lines themselves. Null when no such region exists.
     */
    private fun region(text: String, name: String): String? {
        val begin = Regex("""(?://|<!--)\s*snippet:begin\s+${Regex.escape(name)}\s*(?:-->)?""")
        val end = Regex("""(?://|<!--)\s*snippet:end\s+${Regex.escape(name)}\s*(?:-->)?""")
        val lines = text.lines()
        val start = lines.indexOfFirst { begin.containsMatchIn(it) }
        if (start == -1) return null
        val stop = (start + 1 until lines.size).firstOrNull { end.containsMatchIn(lines[it]) } ?: return null
        return lines.subList(start + 1, stop).joinToString("\n").let { if (it.isEmpty()) it else "$it\n" }
    }

    // ---------------------------------------------------------------------------------------
    // Masking (comparison only — never rewrites either side on disk)
    // ---------------------------------------------------------------------------------------

    /** Applies [maskName] to both sides for comparison; null return means [maskName] is unknown. */
    private fun mask(maskName: String?, expected: String, actual: String): Pair<String, String>? =
        when (maskName) {
            null -> expected to actual
            "duration" -> maskDuration(expected) to maskDuration(actual)
            else -> null
        }

    private fun maskDuration(content: String): String = content.replace(DURATION_MASK, "— Nms")

    private fun relativePath(repoRoot: File, file: File): String =
        repoRoot.toPath().relativize(file.toPath()).toString().replace('\\', '/')
}
